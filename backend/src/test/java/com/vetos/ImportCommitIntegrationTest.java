package com.vetos;

import com.vetos.modules.dataimport.application.CreateImportPreviewUseCase;
import com.vetos.modules.dataimport.application.ImportCommitWorker;
import com.vetos.modules.dataimport.application.ImportRecordCleaner;
import com.vetos.modules.dataimport.application.RecoverInterruptedImportsJob;
import com.vetos.modules.dataimport.application.StartImportCommitUseCase;
import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.ImportBatchStatus;
import com.vetos.modules.dataimport.domain.ImportEntityType;
import com.vetos.modules.dataimport.domain.ImportRecordLink;
import com.vetos.modules.dataimport.domain.ImportRecordLinkRepository;
import com.vetos.modules.dataimport.domain.ImportReport;
import com.vetos.modules.dataimport.domain.ImportTargetWriter;
import com.vetos.modules.dataimport.domain.event.DataImportCommitted;
import com.vetos.modules.dataimport.domain.exception.ImportStateConflictException;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Aktarim isci (worker) entegrasyon testi. Isci testte SENKRON cagrilir (@Async yolu ayrica test edilmez).
 * Canli docker-compose Postgres gerekir; olusturulan her satir @AfterEach'te elle silinir.
 */
@SpringBootTest
@RecordApplicationEvents
class ImportCommitIntegrationTest extends TenantScopedTestSupport {

    private static final String SYSTEM = "KolayVet-Test";
    private static final String[] OWNER_HEADERS = {"kaynak_id", "ad_soyad", "telefon", "tc_kimlik", "il", "Eski Bakiye"};
    private static final String[] PATIENT_HEADERS = {
        "kaynak_id", "sahip_kaynak_id", "ad", "tur", "irk", "cinsiyet", "dogum_tarihi", "kisirlastirilmis",
        "mikrocip_no", "Son Aşı"};

    @Autowired private TenantRepository tenantRepository;
    @Autowired private ImportBatchRepository batchRepository;
    @Autowired private ImportRecordLinkRepository linkRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private CreateImportPreviewUseCase previewUseCase;
    @Autowired private StartImportCommitUseCase startUseCase;
    @Autowired private ImportCommitWorker worker;
    @Autowired private ImportRecordCleaner cleaner;
    @Autowired private RecoverInterruptedImportsJob recoveryJob;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEvents events;
    @MockitoSpyBean private ImportTargetWriter targetWriter;

    private final List<UUID> createdTenantIds = new ArrayList<>();
    private final List<String> createdSpeciesNames = new ArrayList<>();
    private final UUID adminId = UUID.randomUUID();
    private String knownSpecies;

    @BeforeEach
    void pickKnownSpecies() {
        knownSpecies = jdbc.queryForObject("SELECT name FROM species ORDER BY name LIMIT 1", String.class);
    }

    @AfterEach
    void cleanUp() {
        for (UUID t : createdTenantIds) {
            for (String sql : List.of(
                "DELETE FROM import_record_links WHERE tenant_id = ?",
                "DELETE FROM patients WHERE tenant_id = ?",
                "DELETE FROM owners WHERE tenant_id = ?",
                "DELETE FROM import_batches WHERE tenant_id = ?",
                "DELETE FROM audit_log_entries WHERE target_id = ?",
                "DELETE FROM tenants WHERE id = ?")) {
                jdbc.update(sql, t);
            }
        }
        createdTenantIds.clear();
        for (String name : createdSpeciesNames) {
            jdbc.update("DELETE FROM breeds WHERE species_id IN (SELECT id FROM species WHERE name = ?)", name);
            jdbc.update("DELETE FROM species WHERE name = ?", name);
        }
        createdSpeciesNames.clear();
    }

    // ---------- yardimcilar ----------

    private UUID newTenant() {
        UUID id = inRootSession(() -> tenantRepository.save(Tenant.register("Aktarim Isci Testi", null)).getId());
        createdTenantIds.add(id);
        return id;
    }

    private static byte[] workbook(List<String[]> owners, List<String[]> patients) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            fill(wb.createSheet("Sahipler"), OWNER_HEADERS, owners);
            fill(wb.createSheet("Hastalar"), PATIENT_HEADERS, patients);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void fill(Sheet sheet, String[] headers, List<String[]> rows) {
        writeRow(sheet.createRow(0), headers);
        for (int i = 0; i < rows.size(); i++) {
            writeRow(sheet.createRow(i + 1), rows.get(i));
        }
    }

    private static void writeRow(Row row, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] != null) {
                row.createCell(i).setCellValue(cells[i]);
            }
        }
    }

    private static String[] owner(String id, String name, String phone) {
        return new String[] {id, name, phone, null, "Ankara", null};
    }

    private String[] patient(String id, String ownerId, String name) {
        return new String[] {id, ownerId, name, knownSpecies, null, "Dişi", "2020-03-01", "Evet", "MC-" + id, null};
    }

    private UUID preview(UUID tenantId, byte[] file) {
        return previewUseCase.execute(tenantId, SYSTEM, "aktarim.xlsx", file, adminId).getId();
    }

    /** startCommit'i @Async isci tetiklenmeden yapar; isci ardindan testte senkron calistirilir. */
    private void beginCommit(UUID batchId, List<String> rejected) {
        inRootSessionVoid(() -> {
            ImportBatch b = batchRepository.findById(batchId).orElseThrow();
            b.startCommit(rejected);
            batchRepository.save(b);
        });
    }

    private void commit(UUID batchId, List<String> rejected) {
        beginCommit(batchId, rejected);
        worker.run(batchId, adminId, "admin@vetly.test");
    }

    private ImportBatch batch(UUID batchId) {
        return inRootSession(() -> batchRepository.findById(batchId)).orElseThrow();
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private int owners(UUID t) { return count("SELECT count(*) FROM owners WHERE tenant_id = ?", t); }

    private int patients(UUID t) { return count("SELECT count(*) FROM patients WHERE tenant_id = ?", t); }

    private int links(UUID t) { return count("SELECT count(*) FROM import_record_links WHERE tenant_id = ?", t); }

    private List<ImportRecordLink> linksOf(UUID tenantId, UUID batchId) {
        return asTenant(tenantId, () -> linkRepository.findByBatchId(batchId));
    }

    private ImportRecordLink link(UUID tenantId, UUID batchId, ImportEntityType type, String sourceId) {
        return linksOf(tenantId, batchId).stream()
            .filter(l -> l.getEntityType() == type && l.getSourceId().equals(sourceId)).findFirst().orElseThrow();
    }

    private byte[] standardFile() {
        return workbook(
            List.of(owner("S1", "Ayşe Yılmaz", "0532 111 22 33"), owner("S2", "Mehmet Demir", "05442223344")),
            List.of(patient("H1", "S1", "Pamuk"), patient("H2", "S1", "Boncuk"), patient("H3", "S2", "Karabaş")));
    }

    // ---------- testler ----------

    @Test
    void should_createOwnersPatientsAndLinks() {
        UUID t = newTenant();
        UUID batchId = preview(t, standardFile());

        commit(batchId, List.of());

        assertThat(owners(t)).isEqualTo(2);
        assertThat(patients(t)).isEqualTo(3);
        assertThat(links(t)).isEqualTo(5);
        ImportBatch b = batch(batchId);
        assertThat(b.getStatus()).isEqualTo(ImportBatchStatus.COMMITTED);
        assertThat(b.getSourceFile()).isNull();
        assertThat(b.getProcessedRows()).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM import_batches WHERE id = ? AND source_file IS NULL", batchId)).isEqualTo(1);

        // Riza alanlari aktarimda uydurulmaz.
        assertThat(count("SELECT count(*) FROM owners WHERE tenant_id = ? AND (sms_consent OR whatsapp_consent "
            + "OR notification_consent OR marketing_consent)", t)).isZero();
        assertThat(count("SELECT count(*) FROM owners WHERE tenant_id = ? AND city = 'Ankara'", t)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM patients WHERE tenant_id = ? AND neutered AND sex = 'FEMALE' "
            + "AND birth_date = DATE '2020-03-01' AND microchip_no LIKE 'MC-H%'", t)).isEqualTo(3);
        UUID ownerS1 = link(t, batchId, ImportEntityType.OWNER, "S1").getTargetId();
        assertThat(count("SELECT count(*) FROM patients WHERE owner_id = ?", ownerS1)).isEqualTo(2);

        assertThat(count("SELECT count(*) FROM audit_log_entries WHERE target_id = ? AND action = 'DATA_IMPORT_COMMITTED'",
            t)).isEqualTo(1);
        assertThat(events.stream(DataImportCommitted.class)).hasSize(1);
    }

    @Test
    void should_linkMatchedOwnerWithoutCreating() {
        UUID t = newTenant();
        UUID existing = asTenant(t, () -> ownerRepository.save(
            Owner.register(t, "Ayşe Y.", "05321112233", null, null)).getId());
        UUID batchId = preview(t, standardFile());
        assertThat(batch(batchId).getReport().ownerMatches()).extracting(ImportReport.OwnerMatch::sourceId)
            .containsExactly("S1");

        commit(batchId, List.of());

        assertThat(owners(t)).isEqualTo(2); // mevcut + S2
        ImportRecordLink s1 = link(t, batchId, ImportEntityType.OWNER, "S1");
        assertThat(s1.getTargetId()).isEqualTo(existing);
        assertThat(s1.isCreatedByImport()).isFalse();
        assertThat(count("SELECT count(*) FROM patients WHERE owner_id = ?", existing)).isEqualTo(2);
    }

    @Test
    void should_createNewOwner_whenMatchRejected() {
        UUID t = newTenant();
        UUID existing = asTenant(t, () -> ownerRepository.save(
            Owner.register(t, "Ayşe Y.", "05321112233", null, null)).getId());
        UUID batchId = preview(t, standardFile());

        commit(batchId, List.of("S1"));

        assertThat(owners(t)).isEqualTo(3);
        ImportRecordLink s1 = link(t, batchId, ImportEntityType.OWNER, "S1");
        assertThat(s1.getTargetId()).isNotEqualTo(existing);
        assertThat(s1.isCreatedByImport()).isTrue();
        assertThat(count("SELECT count(*) FROM patients WHERE owner_id = ?", existing)).isZero();
    }

    @Test
    void should_createNewOwner_whenMatchAppearsOnlyAfterPreview() {
        UUID t = newTenant();
        UUID batchId = preview(t, standardFile());
        assertThat(batch(batchId).getReport().ownerMatches()).isEmpty();
        // Onizlemeden sonra klinik ayni telefonla sahip girdi: admin bu eslesmeyi hic gormedi/onaylamadi.
        UUID lateOwner = asTenant(t, () -> ownerRepository.save(
            Owner.register(t, "Sonradan Gelen", "05321112233", null, null)).getId());

        commit(batchId, List.of());

        assertThat(owners(t)).isEqualTo(3); // sonradan gelen + S1 + S2
        ImportRecordLink s1 = link(t, batchId, ImportEntityType.OWNER, "S1");
        assertThat(s1.getTargetId()).isNotEqualTo(lateOwner);
        assertThat(s1.isCreatedByImport()).isTrue();
        assertThat(count("SELECT count(*) FROM patients WHERE owner_id = ?", lateOwner)).isZero();
    }

    @Test
    void should_createNewOwner_whenApprovedMatchReplacedByDifferentOwner() {
        UUID t = newTenant();
        UUID approved = asTenant(t, () -> ownerRepository.save(
            Owner.register(t, "Ayşe Y.", "05321112233", null, null)).getId());
        UUID batchId = preview(t, standardFile());
        assertThat(batch(batchId).getReport().ownerMatches()).extracting(ImportReport.OwnerMatch::existingOwnerId)
            .containsExactly(approved);
        // Onaydan once onaylanan sahip silindi, ayni telefonla baska bir sahip girildi.
        jdbc.update("DELETE FROM owners WHERE id = ?", approved);
        UUID other = asTenant(t, () -> ownerRepository.save(
            Owner.register(t, "Başka Biri", "05321112233", null, null)).getId());

        commit(batchId, List.of());

        ImportRecordLink s1 = link(t, batchId, ImportEntityType.OWNER, "S1");
        assertThat(s1.getTargetId()).isNotEqualTo(other);
        assertThat(s1.isCreatedByImport()).isTrue();
        assertThat(count("SELECT count(*) FROM patients WHERE owner_id = ?", other)).isZero();
    }

    @Test
    void should_reuseExistingSourceSystemSpelling_soDedupStillWorks() {
        UUID t = newTenant();
        UUID first = preview(t, standardFile()); // SYSTEM = "KolayVet-Test"
        commit(first, List.of());

        UUID lower = previewUseCase.execute(t, "kolayvet-test", "b.xlsx", standardFile(), adminId).getId();
        UUID spaced = previewUseCase.execute(t, "Kolay Vet-Test", "c.xlsx", standardFile(), adminId).getId();

        assertThat(batch(lower).getSourceSystem()).isEqualTo(SYSTEM);
        assertThat(batch(spaced).getSourceSystem()).isEqualTo(SYSTEM);
        // Ayni sistem sayildigi icin dosyadaki 5 kayit "daha once aktarilmis" gorunur.
        assertThat(batch(spaced).getReport().totals().values().stream()
            .mapToInt(ImportReport.EntityTotals::alreadyImported).sum()).isEqualTo(5);
    }

    @Test
    void should_storeExtrasAsLegacyData() {
        UUID t = newTenant();
        String[] o = {"S1", "Ayşe Yılmaz", "05321112233", "12345678901", null, "150 TL"};
        String[] p = {"H1", "S1", "Pamuk", knownSpecies, null, null, null, null, null, "Kuduz 2023"};
        UUID batchId = preview(t, workbook(List.<String[]>of(o), List.<String[]>of(p)));

        commit(batchId, List.of());

        assertThat(batch(batchId).getStatus()).isEqualTo(ImportBatchStatus.COMMITTED);
        // Gecersiz TC kimlik aktarimi bozmaz: sahip kaydina yazilmaz, eski bilgilerde durur.
        assertThat(link(t, batchId, ImportEntityType.OWNER, "S1").getLegacyData())
            .containsEntry("Eski Bakiye", "150 TL")
            .containsEntry("TC kimlik (geçersiz)", "12345678901");
        assertThat(count("SELECT count(*) FROM owners WHERE tenant_id = ? AND national_id IS NULL", t)).isEqualTo(1);
        assertThat(link(t, batchId, ImportEntityType.PATIENT, "H1").getLegacyData())
            .containsEntry("Son Aşı", "Kuduz 2023");
        assertThat(count("SELECT count(*) FROM patients WHERE tenant_id = ? AND breed_id IS NULL AND sex IS NULL", t))
            .isEqualTo(1);
    }

    @Test
    void should_createUnknownSpeciesOnce_andKeepItAfterRollback() {
        UUID t = newTenant();
        String species = "TestTür-" + UUID.randomUUID().toString().substring(0, 8);
        createdSpeciesNames.add(species);
        String[] p1 = {"H1", "S1", "Pamuk", species, null, null, null, null, null, null};
        String[] p2 = {"H2", "S1", "Boncuk", species.toUpperCase(java.util.Locale.forLanguageTag("tr")),
            null, null, null, null, null, null};
        UUID batchId = preview(t, workbook(List.<String[]>of(owner("S1", "Ayşe", "05321112233")), List.of(p1, p2)));
        assertThat(batch(batchId).getReport().newSpecies()).containsExactly(species);

        commit(batchId, List.of());

        assertThat(count("SELECT count(*) FROM species WHERE name = ?", species)).isEqualTo(1);
        UUID speciesId = jdbc.queryForObject("SELECT id FROM species WHERE name = ?", UUID.class, species);
        assertThat(count("SELECT count(*) FROM patients WHERE tenant_id = ? AND species_id = ?", t, speciesId))
            .isEqualTo(2);

        cleaner.deleteCreatedRecords(batch(batchId));

        assertThat(patients(t)).isZero();
        assertThat(owners(t)).isZero();
        assertThat(links(t)).isZero();
        assertThat(count("SELECT count(*) FROM species WHERE id = ?", speciesId)).isEqualTo(1);
    }

    @Test
    void should_createUnknownBreedOnce_linkPatients_andKeepItAfterRollback() {
        UUID t = newTenant();
        String species = "TestTür-" + UUID.randomUUID().toString().substring(0, 8);
        createdSpeciesNames.add(species); // @AfterEach bu turun irklarini da siler
        String[] p1 = {"H1", "S1", "Pamuk", species, "Hollanda Lop", null, null, null, null, null};
        String[] p2 = {"H2", "S1", "Boncuk", species, "hollanda lop", null, null, null, null, null};
        UUID batchId = preview(t, workbook(List.<String[]>of(owner("S1", "Ayşe", "05321112233")), List.of(p1, p2)));
        assertThat(batch(batchId).getReport().newBreeds())
            .containsExactly(new ImportReport.NewBreed(species, "Hollanda Lop"));

        commit(batchId, List.of());

        UUID speciesId = jdbc.queryForObject("SELECT id FROM species WHERE name = ?", UUID.class, species);
        assertThat(count("SELECT count(*) FROM breeds WHERE species_id = ? AND name = 'Hollanda Lop'", speciesId))
            .isEqualTo(1);
        UUID breedId = jdbc.queryForObject("SELECT id FROM breeds WHERE species_id = ?", UUID.class, speciesId);
        assertThat(count("SELECT count(*) FROM patients WHERE tenant_id = ? AND breed_id = ?", t, breedId))
            .isEqualTo(2);
        assertThat(link(t, batchId, ImportEntityType.PATIENT, "H1").getLegacyData()).doesNotContainKey("Eski ırk");

        cleaner.deleteCreatedRecords(batch(batchId));

        assertThat(patients(t)).isZero();
        assertThat(count("SELECT count(*) FROM breeds WHERE id = ?", breedId)).isEqualTo(1);
    }

    @Test
    void should_createNothing_whenSameFileCommittedTwice() {
        UUID t = newTenant();
        byte[] file = standardFile();
        UUID first = preview(t, file);
        UUID second = preview(t, file); // ikisi de ilk aktarimdan once onizlendi

        commit(first, List.of());
        commit(second, List.of());

        ImportReport report = batch(second).getReport();
        int already = report.totals().values().stream().mapToInt(ImportReport.EntityTotals::alreadyImported).sum();
        assertThat(already).isEqualTo(5);
        assertThat(batch(second).getStatus()).isEqualTo(ImportBatchStatus.COMMITTED);
        assertThat(owners(t)).isEqualTo(2);
        assertThat(patients(t)).isEqualTo(3);
        assertThat(links(t)).isEqualTo(5);
        assertThat(linksOf(t, second)).isEmpty();
    }

    /** 100 sahip + 900 hasta; yazicinin 700. create cagrisi verilen hatayi firlatir (1. hasta parcasi commit edilmis olur). */
    private UUID largeBatchFailingOn700th(UUID t, java.util.function.Supplier<Throwable> failure, AtomicInteger calls) {
        List<String[]> owners = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            owners.add(owner("S" + i, "Sahip " + i, String.format("532%07d", i)));
        }
        List<String[]> patients = new ArrayList<>();
        for (int i = 0; i < 900; i++) {
            patients.add(new String[] {"H" + i, "S" + (i % 100), "Hasta " + i, knownSpecies,
                null, null, null, null, null, null});
        }
        UUID batchId = preview(t, workbook(owners, patients));
        assertThat(batch(batchId).getTotalRows()).isEqualTo(1000);

        Answer<Object> failOn700 = inv -> {
            if (calls.incrementAndGet() == 700) {
                throw failure.get();
            }
            return inv.callRealMethod();
        };
        doAnswer(failOn700).when(targetWriter).createOwner(any(), any());
        doAnswer(failOn700).when(targetWriter).createPatient(any(), any());
        return batchId;
    }

    private void assertRolledBackAndFailed(UUID t, UUID batchId, String messagePart) {
        assertThat(owners(t)).isZero();
        assertThat(patients(t)).isZero();
        assertThat(links(t)).isZero();
        ImportBatch b = batch(batchId);
        assertThat(b.getStatus()).isEqualTo(ImportBatchStatus.FAILED);
        assertThat(b.getErrorMessage()).contains(messagePart);
        assertThat(b.getSourceFile()).isNull();
        assertThat(count("SELECT count(*) FROM audit_log_entries WHERE target_id = ? AND action = 'DATA_IMPORT_FAILED'",
            t)).isEqualTo(1);
    }

    @Test
    void should_rollbackPartialWrites_whenWorkerFails() {
        UUID t = newTenant();
        AtomicInteger calls = new AtomicInteger();
        UUID batchId = largeBatchFailingOn700th(t, () -> new IllegalStateException("Test: 700. yazmada hata"), calls);

        commit(batchId, List.of());

        assertThat(calls.get()).isEqualTo(700); // 100 sahip + 500 hasta (1. parca) + 2. parcada 100
        assertRolledBackAndFailed(t, batchId, "700. yazmada hata");
    }

    @Test
    void should_rollbackPartialWrites_andRethrow_whenWorkerHitsError() {
        UUID t = newTenant();
        AtomicInteger calls = new AtomicInteger();
        // OutOfMemoryError gibi bir JVM Error'u temsil eder (gercek OOME JUnit tarafindan ozel ele alinir).
        UUID batchId = largeBatchFailingOn700th(t, () -> new Error("Test: 700. yazmada JVM hatası"), calls);
        beginCommit(batchId, List.of());

        assertThatThrownBy(() -> worker.run(batchId, adminId, "admin@vetly.test"))
            .isInstanceOf(Error.class).hasMessageContaining("JVM hatası");

        assertThat(calls.get()).isEqualTo(700);
        assertRolledBackAndFailed(t, batchId, "JVM hatası");
    }

    @Test
    void should_rollbackAndFail_interruptedCommittingBatch_onRecovery() {
        UUID t = newTenant();
        UUID batchId = preview(t, standardFile());
        beginCommit(batchId, List.of());
        // Sunucu islem ortasinda kapanmis gibi: bir sahip yazilmis + linki var, parti COMMITTING.
        UUID matchedOwner = asTenant(t, () -> ownerRepository.save(
            Owner.register(t, "Mevcut Sahip", "05550000000", null, null)).getId());
        asTenantVoid(t, () -> {
            UUID created = ownerRepository.save(Owner.register(t, "Yarım Sahip", "05321112233", null, null)).getId();
            linkRepository.saveAll(List.of(
                ImportRecordLink.of(t, batchId, SYSTEM, ImportEntityType.OWNER, "S1", created, true, java.util.Map.of()),
                ImportRecordLink.of(t, batchId, SYSTEM, ImportEntityType.OWNER, "S2", matchedOwner, false,
                    java.util.Map.of())));
        });
        assertThat(owners(t)).isEqualTo(2);

        int recovered = recoveryJob.recover();

        assertThat(recovered).isGreaterThanOrEqualTo(1);
        assertThat(owners(t)).isEqualTo(1); // yalnizca eslesen mevcut sahip kalir
        assertThat(count("SELECT count(*) FROM owners WHERE id = ?", matchedOwner)).isEqualTo(1);
        assertThat(links(t)).isZero();
        ImportBatch b = batch(batchId);
        assertThat(b.getStatus()).isEqualTo(ImportBatchStatus.FAILED);
        assertThat(b.getErrorMessage()).isEqualTo(RecoverInterruptedImportsJob.RECOVERED_MESSAGE);
        assertThat(b.getSourceFile()).isNull();
        assertThat(count("SELECT count(*) FROM audit_log_entries WHERE target_id = ? AND action = 'DATA_IMPORT_FAILED' "
            + "AND platform_admin_email = 'system'", t)).isEqualTo(1);
    }

    @Test
    void should_notPublishPatientRegisteredEvents() {
        UUID t = newTenant();
        UUID batchId = preview(t, standardFile());

        commit(batchId, List.of());

        assertThat(patients(t)).isEqualTo(3);
        List<Object> patientEvents = events.stream()
            .map(e -> e instanceof PayloadApplicationEvent<?> p ? p.getPayload() : e)
            .filter(e -> e.getClass().getPackageName().startsWith("com.vetos.modules.patient.domain.event"))
            .map(e -> (Object) e)
            .toList();
        assertThat(patientEvents).isEmpty();
    }

    @Test
    void should_reject_whenBatchNotPreviewed() {
        UUID t = newTenant();
        UUID discarded = preview(t, standardFile());
        inRootSessionVoid(() -> {
            ImportBatch b = batchRepository.findById(discarded).orElseThrow();
            b.discard();
            batchRepository.save(b);
        });

        assertThatThrownBy(() -> startUseCase.execute(t, discarded, adminId, "admin@vetly.test", List.of()))
            .isInstanceOf(ImportStateConflictException.class);

        UUID previewed = preview(t, standardFile());
        // Isci yalnizca COMMITTING durumundaki partiyi isler.
        assertThatThrownBy(() -> worker.run(previewed, adminId, "admin@vetly.test"))
            .isInstanceOf(IllegalStateException.class);
        assertThat(batch(previewed).getStatus()).isEqualTo(ImportBatchStatus.PREVIEWED);
        assertThat(owners(t)).isZero();
    }

    @Test
    void should_reject_whenReportCannotCommit() {
        UUID t = newTenant();
        UUID empty = preview(t, workbook(List.of(), List.of()));

        assertThatThrownBy(() -> startUseCase.execute(t, empty, adminId, "admin@vetly.test", List.of()))
            .isInstanceOf(ImportStateConflictException.class);
        assertThat(batch(empty).getStatus()).isEqualTo(ImportBatchStatus.PREVIEWED);
    }
}
