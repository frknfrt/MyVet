package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.application.excel.ImportWorkbookParser;
import com.vetos.modules.dataimport.application.excel.ParsedRow;
import com.vetos.modules.dataimport.application.excel.ParsedWorkbook;
import com.vetos.modules.dataimport.application.excel.TurkishText;
import com.vetos.modules.dataimport.domain.ExistingDataLookup;
import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.ImportBatchStatus;
import com.vetos.modules.dataimport.domain.ImportEntityType;
import com.vetos.modules.dataimport.domain.ImportRecordLink;
import com.vetos.modules.dataimport.domain.ImportRecordLinkRepository;
import com.vetos.modules.dataimport.domain.ImportReport;
import com.vetos.modules.dataimport.domain.ImportReport.OwnerMatch;
import com.vetos.modules.dataimport.domain.ImportReport.RowIssue;
import com.vetos.modules.dataimport.domain.ImportTargetWriter;
import com.vetos.modules.dataimport.domain.ImportTargetWriter.NewOwner;
import com.vetos.modules.dataimport.domain.ImportTargetWriter.NewPatient;
import com.vetos.modules.dataimport.domain.event.DataImportCommitted;
import com.vetos.modules.dataimport.domain.event.DataImportFailed;
import com.vetos.modules.dataimport.domain.exception.ImportBatchNotFoundException;
import com.vetos.modules.patient.domain.Sex;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Onaylanan aktarimi arka planda yazar.
 *
 * Akis: dosya yeniden okunur ve yeniden dogrulanir (onizleme ile aktarim arasinda klinik veri girmis olabilir),
 * yeni rapor partiye yazilir; mevcut sahibe baglama yalnizca onizlemede onaylanan (reddedilmeyen) eslesme aynen
 * gecerliyse yapilir, aksi halde yeni sahip olusturulur; once eksik turler, sonra sahipler, sonra hastalar {@value #CHUNK_SIZE}'lik
 * parcalar halinde, her parca kendi transaction'inda ve TenantContext hedef kiraciya ayarli yazilir.
 * Parti durumu (ilerleme/sonuc) kiracisiz kisa transaction'larda guncellenir.
 * Herhangi bir hatada bu partinin olusturdugu kayitlar silinir (bagimlilik kontrolu yok), parti FAILED olur.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ImportCommitWorker {

    static final int CHUNK_SIZE = 500;
    static final String LEGACY_INVALID_NATIONAL_ID_KEY = "TC kimlik (geçersiz)";
    private static final String OWNERS_SHEET = "Sahipler";
    private static final String PATIENTS_SHEET = "Hastalar";

    private final ImportBatchRepository batchRepository;
    private final ImportRecordLinkRepository linkRepository;
    private final ImportWorkbookParser parser;
    private final ImportValidator validator;
    private final ExistingDataLookup lookup;
    private final ImportTargetWriter writer;
    private final ImportRecordCleaner cleaner;
    private final ImportTransactions transactions;
    private final ApplicationEventPublisher events;

    /** StartImportCommitUseCase bu metodu Spring proxy'si uzerinden cagirir (ayni siniftan cagri @Async'i atlar). */
    @Async
    public void runAsync(UUID batchId, UUID adminId, String adminEmail) {
        run(batchId, adminId, adminEmail);
    }

    public void run(UUID batchId, UUID adminId, String adminEmail) {
        ImportBatch batch = transactions.asRoot(() -> batchRepository.findById(batchId))
            .orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        if (batch.getStatus() != ImportBatchStatus.COMMITTING) {
            throw new IllegalStateException(
                "Aktarım işçisi yalnızca COMMITTING durumundaki partiyi işler (şu an: " + batch.getStatus() + ")");
        }
        Outcome outcome;
        try {
            outcome = write(batch);
            updateBatch(batchId, ImportBatch::markCommitted);
        } catch (RuntimeException | Error e) {
            // Error (orn. buyuk dosyada OutOfMemoryError) da yazilmis parcalari birakmasin: temizlik en iyi cabayla
            // yapilir, Error sonra yeniden firlatilir. Temizlik de basarisiz olursa parti COMMITTING kalir ve
            // RecoverInterruptedImportsJob bir sonraki acilista toplar.
            fail(batch, adminId, adminEmail, e);
            if (e instanceof Error error) {
                throw error;
            }
            return;
        }
        publish(new DataImportCommitted(batchId, batch.getTenantId(), adminId, adminEmail,
            "Parti " + batchId + ": " + outcome.describe()));
    }

    // ------------------------------------------------------------------

    private Outcome write(ImportBatch batch) {
        UUID tenantId = batch.getTenantId();
        String system = batch.getSourceSystem();
        // Adminin onayladigi eslesmeler: ONIZLEME raporundakiler (refreshReport'tan once) eksi reddedilenler.
        Map<String, UUID> approved = approvedMatches(batch.getReport(), batch.getRejectedMatchSourceIds());
        ParsedWorkbook wb = parser.parse(new ByteArrayInputStream(batch.getSourceFile()));
        ImportReport report = transactions.asTenant(tenantId, () -> validator.validate(tenantId, system, wb, lookup));
        updateBatch(batch.getId(), b -> b.refreshReport(report));
        if (report.structuralErrors() != null && !report.structuralErrors().isEmpty()) {
            throw new IllegalStateException("Dosya yapısı geçersiz: " + String.join("; ", report.structuralErrors()));
        }

        Plan plan = new Plan(tenantId, batch.getId(), system,
            errorRows(report, OWNERS_SHEET), errorRows(report, PATIENTS_SHEET),
            matches(report), approved, speciesWithNew(report.newSpecies()));
        Outcome outcome = new Outcome();
        int processed = 0;

        Map<String, UUID> ownerIds = new HashMap<>();
        for (List<ParsedRow> chunk : chunks(wb.owners())) {
            OwnerChunk result = transactions.asTenant(tenantId, () -> writeOwners(plan, chunk));
            ownerIds.putAll(result.ownerIds());
            outcome.add(result.created(), result.matched(), 0, result.skipped());
            processed = progress(batch.getId(), processed + chunk.size());
        }
        Map<UUID, Map<String, UUID>> breedCache = new HashMap<>();
        for (List<ParsedRow> chunk : chunks(wb.patients())) {
            int[] r = transactions.asTenant(tenantId, () -> writePatients(plan, chunk, ownerIds, breedCache));
            outcome.add(0, 0, r[0], r[1]);
            processed = progress(batch.getId(), processed + chunk.size());
        }
        return outcome;
    }

    private OwnerChunk writeOwners(Plan plan, List<ParsedRow> chunk) {
        Set<String> already = alreadyImported(plan, ImportEntityType.OWNER, chunk, plan.ownerErrorRows());
        Map<String, UUID> ids = new HashMap<>();
        List<ImportRecordLink> links = new ArrayList<>();
        int created = 0;
        int matched = 0;
        int skipped = 0;
        for (ParsedRow row : chunk) {
            String sourceId = row.values().get("kaynak_id");
            if (plan.ownerErrorRows().contains(row.rowNumber()) || already.contains(sourceId)) {
                skipped++;
                continue;
            }
            Map<String, String> legacy = new LinkedHashMap<>(row.extras());
            // Yalnizca yeniden dogrulanan eslesme, adminin onizlemede onayladigi sahiple AYNIYSA baglanir. Onizlemeden
            // sonra ortaya cikan, baska sahibe kayan ya da kaybolan eslesmede asla otomatik birlestirme yapilmaz.
            UUID match = plan.matches().get(sourceId);
            UUID targetId;
            boolean createdByImport;
            if (match != null && match.equals(plan.approvedMatches().get(sourceId))) {
                targetId = match;
                createdByImport = false;
                matched++;
            } else {
                targetId = writer.createOwner(plan.tenantId(), toOwner(row, legacy));
                createdByImport = true;
                created++;
            }
            ids.put(sourceId, targetId);
            links.add(ImportRecordLink.of(plan.tenantId(), plan.batchId(), plan.system(), ImportEntityType.OWNER,
                sourceId, targetId, createdByImport, legacy));
        }
        linkRepository.saveAll(links);
        return new OwnerChunk(ids, created, matched, skipped);
    }

    private int[] writePatients(Plan plan, List<ParsedRow> chunk, Map<String, UUID> ownerIds,
                                Map<UUID, Map<String, UUID>> breedCache) {
        Set<String> already = alreadyImported(plan, ImportEntityType.PATIENT, chunk, plan.patientErrorRows());
        List<ImportRecordLink> links = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        for (ParsedRow row : chunk) {
            Map<String, String> v = row.values();
            String sourceId = v.get("kaynak_id");
            if (plan.patientErrorRows().contains(row.rowNumber()) || already.contains(sourceId)) {
                skipped++;
                continue;
            }
            String ownerRef = v.get("sahip_kaynak_id");
            UUID ownerId = ownerIds.get(ownerRef);
            if (ownerId == null) {
                // Dosyada olmayan ya da daha once aktarilmis sahip: onceki aktarimin baglantisindan bulunur.
                ownerId = linkRepository.findTarget(plan.tenantId(), plan.system(), ImportEntityType.OWNER, ownerRef)
                    .orElseThrow(() -> new IllegalStateException(
                        "Hasta '" + sourceId + "' için sahip bulunamadı: '" + ownerRef + "'"));
            }
            UUID speciesId = plan.speciesByFold().get(fold(v.get("tur")));
            if (speciesId == null) {
                throw new IllegalStateException("Hasta '" + sourceId + "' için tür bulunamadı: '" + v.get("tur") + "'");
            }
            Map<String, String> legacy = new LinkedHashMap<>(row.extras());
            UUID breedId = null;
            String breed = v.get("irk");
            if (breed != null) {
                Map<String, UUID> breeds = breedCache.computeIfAbsent(speciesId, this::breedsByFold);
                // Vetly'de olmayan irk ortak irk listesine eklenir (onizlemede "yeni irklar" olarak gosterildi).
                // Ayni transaction'da yazilir; irk geri almada silinmez (tur gibi).
                breedId = breeds.computeIfAbsent(fold(breed), k -> writer.createBreed(speciesId, breed.strip()));
            }
            NewPatient patient = new NewPatient(ownerId, speciesId, breedId, v.get("ad").strip(),
                sex(v.get("cinsiyet")), birthDate(v.get("dogum_tarihi")), yes(v.get("kisirlastirilmis")),
                v.get("mikrocip_no"), v.get("pasaport_no"), v.get("renk"), v.get("notlar"));
            UUID targetId = writer.createPatient(plan.tenantId(), patient);
            created++;
            links.add(ImportRecordLink.of(plan.tenantId(), plan.batchId(), plan.system(), ImportEntityType.PATIENT,
                sourceId, targetId, true, legacy));
        }
        linkRepository.saveAll(links);
        return new int[] {created, skipped};
    }

    private NewOwner toOwner(ParsedRow row, Map<String, String> legacy) {
        Map<String, String> v = row.values();
        String nationalId = v.get("tc_kimlik");
        if (nationalId != null && !writer.isValidNationalId(nationalId.strip())) {
            // Gecersiz TC kimlik tum aktarimi bozmasin: kayda yazilmaz, eski bilgilerde saklanir.
            legacy.put(LEGACY_INVALID_NATIONAL_ID_KEY, nationalId);
            nationalId = null;
        }
        return new NewOwner(v.get("ad_soyad").strip(), v.get("telefon").strip(), v.get("ikinci_telefon"),
            v.get("e_posta"), nationalId == null ? null : nationalId.strip(), v.get("adres"), v.get("il"),
            v.get("ilce"), v.get("notlar"));
    }

    private void fail(ImportBatch batch, UUID adminId, String adminEmail, Throwable cause) {
        UUID batchId = batch.getId();
        log.error("Veri aktarımı başarısız, yazılan kayıtlar siliniyor: batchId={}, tenantId={}",
            batchId, batch.getTenantId(), cause);
        String reason = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        String message = "Aktarım sırasında hata oluştu, yazılan kayıtlar geri alındı: " + reason;
        try {
            ImportRecordCleaner.Result removed = cleaner.deleteCreatedRecords(batch);
            log.info("Başarısız aktarım temizlendi: batchId={}, {}", batchId, removed);
        } catch (RuntimeException | Error cleanupError) {
            log.error("Başarısız aktarımın kayıtları silinemedi: batchId={}", batchId, cleanupError);
            message = "Aktarım sırasında hata oluştu: " + reason
                + " (Yazılan kayıtlar geri alınamadı: " + cleanupError.getMessage() + ")";
        }
        String errorMessage = message;
        try {
            updateBatch(batchId, b -> b.markFailed(errorMessage));
        } catch (RuntimeException | Error statusError) {
            log.error("Aktarım FAILED olarak işaretlenemedi: batchId={}", batchId, statusError);
        }
        publish(new DataImportFailed(batchId, batch.getTenantId(), adminId, adminEmail,
            "Parti " + batchId + ": " + errorMessage));
    }

    // ---- yardimcilar ----

    private void updateBatch(UUID batchId, Consumer<ImportBatch> change) {
        transactions.asRootVoid(() -> {
            ImportBatch b = batchRepository.findById(batchId).orElseThrow(() -> new ImportBatchNotFoundException(batchId));
            change.accept(b);
            batchRepository.save(b);
        });
    }

    private int progress(UUID batchId, int processed) {
        updateBatch(batchId, b -> b.progress(processed));
        return processed;
    }

    /** Audit dinleyicisi hata verse bile yazilmis aktarim etkilenmez. */
    private void publish(Object event) {
        try {
            events.publishEvent(event);
        } catch (RuntimeException | Error e) {
            log.error("Aktarım olayı yayınlanamadı: {}", event, e);
        }
    }

    private Set<String> alreadyImported(Plan plan, ImportEntityType type, List<ParsedRow> chunk, Set<Integer> errorRows) {
        List<String> ids = chunk.stream().filter(r -> !errorRows.contains(r.rowNumber()))
            .map(r -> r.values().get("kaynak_id")).toList();
        return linkRepository.existingSourceIds(plan.tenantId(), plan.system(), type, ids);
    }

    private Map<String, UUID> speciesWithNew(List<String> newSpecies) {
        Map<String, UUID> byFold = speciesByFold();
        for (String name : newSpecies == null ? List.<String>of() : newSpecies) {
            String key = fold(name);
            // Onizlemeden bu yana baskasi eklemis olabilir: var olani kullan.
            if (!byFold.containsKey(key)) {
                byFold.put(key, transactions.asRoot(() -> writer.createSpecies(name.strip())));
            }
        }
        return byFold;
    }

    private Map<String, UUID> speciesByFold() {
        Map<String, UUID> byFold = new HashMap<>();
        for (ExistingDataLookup.SpeciesRef s : transactions.asRoot(lookup::species)) {
            byFold.putIfAbsent(fold(s.name()), s.id());
        }
        return byFold;
    }

    private Map<String, UUID> breedsByFold(UUID speciesId) {
        Map<String, UUID> byFold = new HashMap<>();
        for (ExistingDataLookup.BreedRef b : lookup.breeds(speciesId)) {
            byFold.putIfAbsent(fold(b.name()), b.id());
        }
        return byFold;
    }

    private static Set<Integer> errorRows(ImportReport report, String sheet) {
        Set<Integer> rows = new HashSet<>();
        for (RowIssue issue : report.errors()) {
            if (sheet.equals(issue.sheet())) {
                rows.add(issue.rowNumber());
            }
        }
        return rows;
    }

    private static Map<String, UUID> matches(ImportReport report) {
        Map<String, UUID> result = new HashMap<>();
        if (report == null || report.ownerMatches() == null) {
            return result;
        }
        for (OwnerMatch m : report.ownerMatches()) {
            result.put(m.sourceId(), m.existingOwnerId());
        }
        return result;
    }

    private static Map<String, UUID> approvedMatches(ImportReport previewReport, List<String> rejected) {
        Map<String, UUID> result = matches(previewReport);
        if (rejected != null) {
            rejected.forEach(result::remove);
        }
        return result;
    }

    private static List<List<ParsedRow>> chunks(List<ParsedRow> rows) {
        List<List<ParsedRow>> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i += CHUNK_SIZE) {
            out.add(rows.subList(i, Math.min(rows.size(), i + CHUNK_SIZE)));
        }
        return out;
    }

    private static String fold(String text) {
        return TurkishText.fold(text).strip();
    }

    private static Sex sex(String text) {
        if (text == null) {
            return null;
        }
        return switch (fold(text)) {
            case "erkek", "e" -> Sex.MALE;
            case "disi", "d" -> Sex.FEMALE;
            default -> null; // dogrulayici bu satiri zaten hatali sayar
        };
    }

    private static boolean yes(String text) {
        String f = fold(text);
        return f.equals("evet") || f.equals("e");
    }

    private static java.time.LocalDate birthDate(String text) {
        return text == null ? null : ImportValidator.parseDate(text);
    }

    private record Plan(UUID tenantId, UUID batchId, String system, Set<Integer> ownerErrorRows,
                        Set<Integer> patientErrorRows, Map<String, UUID> matches, Map<String, UUID> approvedMatches,
                        Map<String, UUID> speciesByFold) {
    }

    private record OwnerChunk(Map<String, UUID> ownerIds, int created, int matched, int skipped) {
    }

    private static final class Outcome {
        private int ownersCreated;
        private int ownersMatched;
        private int patientsCreated;
        private int skipped;

        void add(int owners, int matched, int patients, int skippedRows) {
            ownersCreated += owners;
            ownersMatched += matched;
            patientsCreated += patients;
            skipped += skippedRows;
        }

        String describe() {
            return ownersCreated + " sahip oluşturuldu, " + ownersMatched + " sahip eşleştirildi, "
                + patientsCreated + " hasta oluşturuldu, " + skipped + " satır atlandı";
        }
    }
}
