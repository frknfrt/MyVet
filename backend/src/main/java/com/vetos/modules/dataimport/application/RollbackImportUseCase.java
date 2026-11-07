package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.domain.Dependent;
import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.ImportBatchStatus;
import com.vetos.modules.dataimport.domain.ImportDependencyInspector;
import com.vetos.modules.dataimport.domain.ImportEntityType;
import com.vetos.modules.dataimport.domain.ImportRecordDeleter;
import com.vetos.modules.dataimport.domain.ImportRecordLink;
import com.vetos.modules.dataimport.domain.ImportRecordLinkRepository;
import com.vetos.modules.dataimport.domain.event.DataImportRolledBack;
import com.vetos.modules.dataimport.domain.exception.ImportBatchNotFoundException;
import com.vetos.modules.dataimport.domain.exception.ImportStateConflictException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Aktarimi geri alir. Parti satiri kilitlenir (FOR UPDATE: ayni partinin es zamanli geri alinmasini/durum
 * degisikligini engeller); bagimlilik kontrolu, silme ve ROLLED_BACK isareti TEK transaction'da (partinin
 * kiracisinda) yapilir. (ImportBatch'te @TenantId yok, bu yuzden kiraci oturumunda da okunup yazilabilir.)
 * Parti kilidi klinigin owners/patients'a yeni bagimli kayit eklemesini ENGELLEMEZ: kontrol ile silme arasinda
 * eklenen kayit silmede yabanci anahtar ihlaline yol acar; bu durumda transaction geri alinir ve
 * ImportStateConflictException (409, "tekrar deneyin") firlatilir.
 * Engeller: owners/patients'a FK ile bagli, partiye ait olmayan satirlar ve BASKA partilerin bu partinin olusturdugu
 * kayitlara baglanan linkleri (silinirse o linkler bos kimlige isaret ederdi).
 * Engel varsa hicbir sey silinmez, durum degismez. Yalnizca created_by_import=true kayitlar silinir
 * (ImportRecordCleaner ile ayni deleter); eslesen sahiplere ve turlere dokunulmaz.
 * Kaynak durumlar: COMMITTED, ve linki hala duran FAILED (otomatik temizligi basarisiz olmus aktarim).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RollbackImportUseCase {

    static final String CONCURRENT_DEPENDENT_MESSAGE =
        "Geri alma sırasında klinik bu kayıtlara yeni kayıt ekledi; tekrar deneyin.";
    static final String LINKS_TABLE = "import_record_links";
    static final String LINK_TARGET_COLUMN = "target_id";
    private static final int MAX_LINK_BLOCKERS = 100;

    private final ImportBatchRepository batchRepository;
    private final ImportRecordLinkRepository linkRepository;
    private final ImportDependencyInspector inspector;
    private final ImportRecordDeleter deleter;
    private final ImportTransactions transactions;
    private final ApplicationEventPublisher events;

    public RollbackResult execute(UUID tenantId, UUID batchId, UUID adminId, String adminEmail) {
        Done done;
        try {
            done = transactions.asTenant(tenantId, () -> rollback(tenantId, batchId));
        } catch (DataIntegrityViolationException e) {
            // Yaris: kontrol temizdi ama silmeden once klinik bu kayitlara bagli yeni kayit ekledi. Transaction geri
            // alindi, hicbir sey silinmedi.
            log.info("Geri alma sırasında yeni bağımlı kayıt eklendi, geri alma iptal: batchId={}", batchId, e);
            throw new ImportStateConflictException(CONCURRENT_DEPENDENT_MESSAGE);
        }
        if (!done.result().rolledBack()) {
            return done.result();
        }
        try {
            events.publishEvent(new DataImportRolledBack(batchId, tenantId, adminId, adminEmail,
                "Parti " + batchId + ": " + done.describe()));
        } catch (RuntimeException | Error e) {
            // Geri alma zaten kalici; denetim dinleyicisi hatasi sonucu bozmasin.
            log.error("Geri alma olayı yayınlanamadı: batchId={}", batchId, e);
        }
        return done.result();
    }

    private Done rollback(UUID tenantId, UUID batchId) {
        ImportBatch batch = batchRepository.findByIdForUpdate(batchId)
            .filter(b -> b.getTenantId().equals(tenantId))
            .orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        List<ImportRecordLink> links = linkRepository.findByBatchId(batchId);
        ImportBatchStatus status = batch.getStatus();
        boolean failedWithLinks = status == ImportBatchStatus.FAILED && !links.isEmpty();
        if (status != ImportBatchStatus.COMMITTED && !failedWithLinks) {
            throw new ImportStateConflictException(status == ImportBatchStatus.FAILED
                ? "Başarısız aktarımın geri alınacak kaydı yok"
                : "Aktarım yalnızca tamamlandıktan sonra geri alınabilir (şu an: " + status + ")");
        }

        Set<UUID> ownerIds = createdTargets(links, ImportEntityType.OWNER);
        Set<UUID> patientIds = createdTargets(links, ImportEntityType.PATIENT);
        // Sahibe bagli, bu partinin olusturdugu hastalar engel degil; klinigin eklediği hastalar engel.
        List<Dependent> blockers = new ArrayList<>(inspector.findForeignDependents("owners", ownerIds, patientIds));
        blockers.addAll(inspector.findForeignDependents("patients", patientIds, Set.of()));
        // Baska partilerin (eslesme ya da olusturma) bu partinin kayitlarina baglanan linkleri de engeldir.
        Set<UUID> created = new HashSet<>(ownerIds);
        created.addAll(patientIds);
        for (ImportRecordLink other : linkRepository.findLinksOfOtherBatchesToTargets(batchId, created, MAX_LINK_BLOCKERS)) {
            blockers.add(new Dependent(LINKS_TABLE, other.getId(), LINK_TARGET_COLUMN));
        }
        if (!blockers.isEmpty()) {
            return new Done(new RollbackResult(false, blockers), "");
        }

        int patients = deleter.deleteCreatedPatients(tenantId, batchId);
        int owners = deleter.deleteCreatedOwners(tenantId, batchId);
        deleter.deleteLinks(tenantId, batchId);
        try {
            batch.markRolledBack();
        } catch (IllegalStateException e) {
            throw new ImportStateConflictException(e.getMessage());
        }
        batchRepository.save(batch);
        return new Done(new RollbackResult(true, List.of()), owners + " sahip ve " + patients + " hasta silindi");
    }

    private static Set<UUID> createdTargets(List<ImportRecordLink> links, ImportEntityType type) {
        return links.stream()
            .filter(l -> l.getEntityType() == type && l.isCreatedByImport())
            .map(ImportRecordLink::getTargetId)
            .collect(Collectors.toSet());
    }

    private record Done(RollbackResult result, String describe) {
    }
}
