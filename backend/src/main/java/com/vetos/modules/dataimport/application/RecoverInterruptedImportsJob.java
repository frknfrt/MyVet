package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.ImportBatchStatus;
import com.vetos.modules.dataimport.domain.event.DataImportFailed;
import com.vetos.platform.concurrency.AdvisoryLock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Acilista yarida kalmis aktarimlari toplar. Isci calisirken sunucu kapanirsa (deploy/restart) parti COMMITTING'de
 * kalir ve onceki parcalarin kayitlari durur. Bu is her COMMITTING partinin olusturdugu kayitlari, iscinin hata
 * yoluyla AYNI temizlikle (ImportRecordCleaner) siler ve partiyi FAILED yapar. Her parti ayri ele alinir: birinin
 * hatasi digerlerini durdurmaz.
 *
 * VARSAYIM: uretim TEK instance calisir. Acilista calisan baska bir isci olamayacagi icin her COMMITTING parti
 * sahipsiz (yetim) sayilir. Birden fazla instance ya da kesintisiz (rolling) deploy'a gecilirse bu varsayim bozulur:
 * baska bir instance'in suren aktarimi yanlislikla geri alinabilir; o zaman partiye sahip/heartbeat bilgisi eklenmeli.
 * Gelistirme ortaminda testler ve :8080 ayni veritabanini paylastigi icin bir test baglaminin acilisi da bu isi
 * tetikler.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RecoverInterruptedImportsJob {

    static final long RECOVERY_LOCK_KEY = 7_301_011; // 7_301_001 / 7_301_002 / 7_301_010'dan farkli
    static final String SYSTEM_ACTOR = "system";
    public static final String RECOVERED_MESSAGE =
        "Sunucu yeniden başlatıldığı için aktarım yarıda kaldı; eklenen kayıtlar geri alındı.";

    private final ImportBatchRepository batchRepository;
    private final ImportRecordCleaner cleaner;
    private final ImportTransactions transactions;
    private final AdvisoryLock advisoryLock;
    private final ApplicationEventPublisher events;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            recover();
        } catch (RuntimeException e) {
            // Kurtarma hatasi uygulamanin acilmasini engellemesin.
            log.error("Yarıda kalan aktarımlar toplanamadı", e);
        }
    }

    /**
     * Kilit, onu alan (root) transaction'a baglidir; temizlik ve durum guncellemesi kendi REQUIRES_NEW
     * transaction'larinda calisir. Olaylar kilit transaction'i bittikten SONRA yayinlanir: audit hatasi kilit
     * transaction'ini rollback-only yapmasin.
     *
     * @return FAILED yapilan parti sayisi
     */
    public int recover() {
        List<DataImportFailed> failed = transactions.asRoot(() -> {
            List<DataImportFailed> out = new ArrayList<>();
            if (!advisoryLock.tryAcquire(RECOVERY_LOCK_KEY)) {
                log.info("Aktarım kurtarma kilidi başka bir instance'da -- atlanıyor");
                return out;
            }
            // Yalnizca kimlikler kullanilir; her parti asagida kendi taze transaction'inda kilitli yeniden okunur.
            List<UUID> ids = batchRepository.findByStatus(ImportBatchStatus.COMMITTING).stream()
                .map(ImportBatch::getId).toList();
            for (UUID id : ids) {
                try {
                    recoverOne(id).ifPresent(out::add);
                } catch (RuntimeException e) {
                    log.error("Yarıda kalan aktarım FAILED yapılamadı: batchId={}", id, e);
                }
            }
            return out;
        });
        for (DataImportFailed event : failed) {
            try {
                events.publishEvent(event);
            } catch (RuntimeException e) {
                log.error("Aktarım olayı yayınlanamadı: {}", event, e);
            }
        }
        if (!failed.isEmpty()) {
            log.warn("{} yarıda kalmış aktarım geri alındı ve FAILED yapıldı", failed.size());
        }
        return failed.size();
    }

    private Optional<DataImportFailed> recoverOne(UUID batchId) {
        // Taze (REQUIRES_NEW) transaction'da kilitli okuma: bu arada durumu degistiyse dokunulmaz.
        ImportBatch batch = transactions.asRoot(() -> batchRepository.findByIdForUpdate(batchId)
            .filter(b -> b.getStatus() == ImportBatchStatus.COMMITTING).orElse(null));
        if (batch == null) {
            return Optional.empty();
        }
        String message = RECOVERED_MESSAGE;
        try {
            ImportRecordCleaner.Result removed = cleaner.deleteCreatedRecords(batch);
            log.info("Yarıda kalan aktarım temizlendi: batchId={}, {}", batch.getId(), removed);
        } catch (RuntimeException cleanupError) {
            log.error("Yarıda kalan aktarımın kayıtları silinemedi: batchId={}", batch.getId(), cleanupError);
            message = "Sunucu yeniden başlatıldığı için aktarım yarıda kaldı; eklenen kayıtlar geri alınamadı: "
                + cleanupError.getMessage();
        }
        String errorMessage = message;
        boolean marked = transactions.asRoot(() -> batchRepository.findByIdForUpdate(batchId)
            .filter(b -> b.getStatus() == ImportBatchStatus.COMMITTING)
            .map(b -> {
                b.markFailed(errorMessage);
                batchRepository.save(b);
                return true;
            })
            .orElse(false));
        if (!marked) {
            return Optional.empty();
        }
        return Optional.of(new DataImportFailed(batchId, batch.getTenantId(), batch.getCreatedByAdminId(),
            SYSTEM_ACTOR, "Parti " + batchId + ": " + errorMessage));
    }
}
