package com.vetos;

import com.vetos.modules.dataimport.application.CreateImportPreviewUseCase;
import com.vetos.modules.dataimport.application.ImportCommitWorker;
import com.vetos.modules.dataimport.application.RollbackImportUseCase;
import com.vetos.modules.dataimport.application.RollbackResult;
import com.vetos.modules.dataimport.domain.Dependent;
import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.ImportBatchStatus;
import com.vetos.modules.dataimport.domain.ImportDependencyInspector;
import com.vetos.modules.dataimport.domain.ImportEntityType;
import com.vetos.modules.dataimport.domain.ImportRecordLink;
import com.vetos.modules.dataimport.domain.ImportRecordLinkRepository;
import com.vetos.modules.dataimport.domain.ImportTargetWriter;
import com.vetos.modules.dataimport.domain.event.DataImportRolledBack;
import com.vetos.modules.dataimport.domain.exception.ImportBatchNotFoundException;
import com.vetos.modules.dataimport.domain.exception.ImportStateConflictException;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import com.vetos.platform.security.JwtTokenProvider;
import com.vetos.platform.security.PlatformAdminJwtTokenProvider;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aktarim geri alma entegrasyon testi. Canli docker-compose Postgres gerekir; olusturulan her satir
 * @AfterEach'te elle silinir. Aktarim testte gercek isciyle SENKRON yapilir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class ImportRollbackIntegrationTest extends TenantScopedTestSupport {

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
    @Autowired private ImportCommitWorker worker;
    @Autowired private RollbackImportUseCase rollbackUseCase;
    @MockitoSpyBean private ImportDependencyInspector inspector;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ImportTargetWriter writer;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEvents events;
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PlatformAdminJwtTokenProvider platformAdminJwtTokenProvider;

    private final List<UUID> createdTenantIds = new ArrayList<>();
    private final UUID adminId = UUID.randomUUID();
    private static final String ADMIN_EMAIL = "admin@vetly.test";
    private String knownSpecies;
    private UUID knownSpeciesId;

    @BeforeEach
    void pickKnownSpecies() {
        knownSpecies = jdbc.queryForObject("SELECT name FROM species ORDER BY name LIMIT 1", String.class);
        knownSpeciesId = jdbc.queryForObject("SELECT id FROM species WHERE name = ?", UUID.class, knownSpecies);
    }

    @AfterEach
    void cleanUp() {
        for (UUID t : createdTenantIds) {
            for (String sql : List.of(
                "DELETE FROM consent_records WHERE owner_id IN (SELECT id FROM owners WHERE tenant_id = ?)",
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
    }

    // ---------- yardimcilar ----------

    private UUID newTenant() {
        UUID id = inRootSession(() -> tenantRepository.save(Tenant.register("Aktarim Geri Alma Testi", null)).getId());
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

    /** Onizle + senkron aktar; sonunda parti COMMITTED. */
    private UUID importFile(UUID tenantId, byte[] file) {
        UUID batchId = previewUseCase.execute(tenantId, SYSTEM, "aktarim.xlsx", file, adminId).getId();
        inRootSessionVoid(() -> {
            ImportBatch b = batchRepository.findById(batchId).orElseThrow();
            b.startCommit(List.of());
            batchRepository.save(b);
        });
        worker.run(batchId, adminId, ADMIN_EMAIL);
        return batchId;
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

    private UUID targetOf(UUID tenantId, UUID batchId, ImportEntityType type, String sourceId) {
        return asTenant(tenantId, () -> linkRepository.findByBatchId(batchId)).stream()
            .filter(l -> l.getEntityType() == type && l.getSourceId().equals(sourceId))
            .findFirst().orElseThrow().getTargetId();
    }

    private RollbackResult rollback(UUID tenantId, UUID batchId) {
        return rollbackUseCase.execute(tenantId, batchId, adminId, ADMIN_EMAIL);
    }

    private UUID clinicPatientFor(UUID tenantId, UUID ownerId, String name) {
        return asTenant(tenantId, () -> writer.createPatient(tenantId, new ImportTargetWriter.NewPatient(
            ownerId, knownSpeciesId, null, name, null, null, false, null, null, null, null)));
    }

    // ---------- testler ----------

    @Test
    void should_deleteOnlyBatchCreatedRecords() {
        UUID t = newTenant();
        UUID clinicOwner = asTenant(t, () -> ownerRepository.save(
            Owner.register(t, "Klinik Sahibi", "05550000000", null, null)).getId());
        UUID clinicPatient = clinicPatientFor(t, clinicOwner, "Klinik Hastası");
        // S1 mevcut "Ayşe Y." ile eslesir; S2 yeni sahip.
        UUID matched = asTenant(t, () -> ownerRepository.save(
            Owner.register(t, "Ayşe Y.", "05321112233", null, null)).getId());
        UUID batchId = importFile(t, workbook(
            List.<String[]>of(owner("S1", "Ayşe Yılmaz", "0532 111 22 33"), owner("S2", "Mehmet Demir", "05442223344")),
            List.<String[]>of(patient("H1", "S1", "Pamuk"), patient("H2", "S2", "Karabaş"))));
        assertThat(batch(batchId).getStatus()).isEqualTo(ImportBatchStatus.COMMITTED);
        assertThat(owners(t)).isEqualTo(3); // klinik + eslesen + S2
        assertThat(patients(t)).isEqualTo(3);

        RollbackResult result = rollback(t, batchId);

        assertThat(result.rolledBack()).isTrue();
        assertThat(result.blockers()).isEmpty();
        assertThat(owners(t)).isEqualTo(2); // klinik sahibi + eslesen sahip
        assertThat(count("SELECT count(*) FROM owners WHERE id IN (?, ?)", clinicOwner, matched)).isEqualTo(2);
        assertThat(patients(t)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM patients WHERE id = ?", clinicPatient)).isEqualTo(1);
        assertThat(links(t)).isZero();
        ImportBatch b = batch(batchId);
        assertThat(b.getStatus()).isEqualTo(ImportBatchStatus.ROLLED_BACK);
        assertThat(b.getRolledBackAt()).isNotNull();
        assertThat(count("SELECT count(*) FROM audit_log_entries WHERE target_id = ? "
            + "AND action = 'DATA_IMPORT_ROLLED_BACK'", t)).isEqualTo(1);
        assertThat(events.stream(DataImportRolledBack.class)).hasSize(1);
    }

    @Test
    void should_block_whenClinicAddedPatientToImportedOwner() {
        UUID t = newTenant();
        UUID batchId = importFile(t, workbook(
            List.<String[]>of(owner("S1", "Ayşe Yılmaz", "05321112233")), List.<String[]>of(patient("H1", "S1", "Pamuk"))));
        UUID importedOwner = targetOf(t, batchId, ImportEntityType.OWNER, "S1");
        UUID clinicPatient = clinicPatientFor(t, importedOwner, "Sonradan Eklenen");

        RollbackResult result = rollback(t, batchId);

        assertThat(result.rolledBack()).isFalse();
        assertThat(result.blockers()).hasSize(1);
        Dependent blocker = result.blockers().get(0);
        assertThat(blocker.table()).isEqualTo("patients");
        assertThat(blocker.rowId()).isEqualTo(clinicPatient);
        assertThat(blocker.column()).isEqualTo("owner_id");
        assertThat(owners(t)).isEqualTo(1);
        assertThat(patients(t)).isEqualTo(2);
        assertThat(links(t)).isEqualTo(2);
        assertThat(batch(batchId).getStatus()).isEqualTo(ImportBatchStatus.COMMITTED);
        assertThat(events.stream(DataImportRolledBack.class)).isEmpty();
    }

    @Test
    void should_block_whenAnyTableReferencesImportedOwner() {
        UUID t = newTenant();
        UUID batchId = importFile(t, workbook(List.<String[]>of(owner("S1", "Ayşe Yılmaz", "05321112233")), List.of()));
        UUID importedOwner = targetOf(t, batchId, ImportEntityType.OWNER, "S1");
        UUID consentId = UUID.randomUUID();
        jdbc.update("INSERT INTO consent_records (id, tenant_id, owner_id, consent_type, granted, granted_at) "
            + "VALUES (?, ?, ?, 'SMS', true, now())", consentId, t, importedOwner);

        RollbackResult result = rollback(t, batchId);

        assertThat(result.rolledBack()).isFalse();
        assertThat(result.blockers()).containsExactly(new Dependent("consent_records", consentId, "owner_id"));
        assertThat(owners(t)).isEqualTo(1);
        assertThat(batch(batchId).getStatus()).isEqualTo(ImportBatchStatus.COMMITTED);
    }

    @Test
    void should_notBlockOnRecordsOfSameBatch() {
        UUID t = newTenant();
        UUID batchId = importFile(t, workbook(
            List.<String[]>of(owner("S1", "Ayşe Yılmaz", "05321112233")),
            List.<String[]>of(patient("H1", "S1", "Pamuk"), patient("H2", "S1", "Boncuk"))));
        UUID importedOwner = targetOf(t, batchId, ImportEntityType.OWNER, "S1");
        assertThat(count("SELECT count(*) FROM patients WHERE owner_id = ?", importedOwner)).isEqualTo(2);

        assertThat(inspector.findForeignDependents("owners", List.of(importedOwner),
            List.of(targetOf(t, batchId, ImportEntityType.PATIENT, "H1"),
                targetOf(t, batchId, ImportEntityType.PATIENT, "H2")))).isEmpty();
        RollbackResult result = rollback(t, batchId);

        assertThat(result.rolledBack()).isTrue();
        assertThat(owners(t)).isZero();
        assertThat(patients(t)).isZero();
    }

    @Test
    void should_block_whenAnotherBatchLinksToCreatedOwner() {
        UUID t = newTenant();
        UUID batchA = importFile(t, workbook(List.<String[]>of(owner("S1", "Ayşe Yılmaz", "05321112233")), List.of()));
        UUID ownerA = targetOf(t, batchA, ImportEntityType.OWNER, "S1");
        // Baska bir kaynak programdan B partisi ayni telefonla A'nin olusturdugu sahibe eslesir (yalnizca link).
        UUID batchB = previewUseCase.execute(t, "Diger-Test", "b.xlsx",
            workbook(List.<String[]>of(owner("D1", "Ayşe Y.", "0532 111 22 33")), List.of()), adminId).getId();
        inRootSessionVoid(() -> {
            ImportBatch b = batchRepository.findById(batchB).orElseThrow();
            b.startCommit(List.of());
            batchRepository.save(b);
        });
        worker.run(batchB, adminId, ADMIN_EMAIL);
        ImportRecordLink linkB = asTenant(t, () -> linkRepository.findByBatchId(batchB)).get(0);
        assertThat(linkB.getTargetId()).isEqualTo(ownerA);
        assertThat(linkB.isCreatedByImport()).isFalse();

        RollbackResult result = rollback(t, batchA);

        assertThat(result.rolledBack()).isFalse();
        assertThat(result.blockers()).containsExactly(new Dependent("import_record_links", linkB.getId(), "target_id"));
        assertThat(owners(t)).isEqualTo(1);
        assertThat(links(t)).isEqualTo(2);
        assertThat(batch(batchA).getStatus()).isEqualTo(ImportBatchStatus.COMMITTED);
    }

    @Test
    void should_return409_whenClinicInsertsDependentDuringRollback() {
        UUID t = newTenant();
        UUID batchId = importFile(t, workbook(
            List.<String[]>of(owner("S1", "Ayşe Yılmaz", "05321112233")), List.<String[]>of(patient("H1", "S1", "Pamuk"))));
        UUID importedOwner = targetOf(t, batchId, ImportEntityType.OWNER, "S1");
        // Yaris: bagimlilik kontrolu temiz gordu, silmeden once klinik sahibe hasta ekledi.
        clinicPatientFor(t, importedOwner, "Yarışta Eklenen");
        doReturn(List.of()).when(inspector).findForeignDependents(anyString(), any(), any());

        assertThatThrownBy(() -> rollback(t, batchId))
            .isInstanceOf(ImportStateConflictException.class)
            .hasMessage("Geri alma sırasında klinik bu kayıtlara yeni kayıt ekledi; tekrar deneyin.");

        assertThat(owners(t)).isEqualTo(1);
        assertThat(patients(t)).isEqualTo(2);
        assertThat(links(t)).isEqualTo(2);
        assertThat(batch(batchId).getStatus()).isEqualTo(ImportBatchStatus.COMMITTED);
        assertThat(events.stream(DataImportRolledBack.class)).isEmpty();
    }

    /**
     * Fail-closed: CASCADE / SET NULL gibi bir yabanci anahtar "engel yok" sonucunu gecersiz kilar.
     * Deneme tablosu transaction icinde olusturulur ve transaction geri alinir (DDL Postgres'te transactional):
     * diger oturumlar tabloyu hic gormez, paylasilan DB'de iz kalmaz. (TEMP tablo kalici tabloya FK veremez.)
     */
    @Test
    void should_failClosed_onUnsupportedForeignKey_inInspector() {
        for (String action : List.of("CASCADE", "SET NULL")) {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> {
                status.setRollbackOnly();
                jdbc.execute("CREATE TABLE import_fk_guard_probe (id uuid PRIMARY KEY, "
                    + "owner_id uuid REFERENCES owners(id) ON DELETE " + action + ")");
                assertThatThrownBy(() -> inspector.findForeignDependents("owners", List.of(UUID.randomUUID()), List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("Desteklenmeyen yabancı anahtar: ")
                    .hasMessageContaining("import_fk_guard_probe");
            });
        }
        assertThat(count("SELECT count(*) FROM pg_class WHERE relname = 'import_fk_guard_probe'")).isZero();
    }

    @Test
    void should_rejectUnknownTable_inInspector() {
        assertThatThrownBy(() -> inspector.findForeignDependents("staff_users", List.of(UUID.randomUUID()), List.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inspector.findForeignDependents("owners; DROP TABLE owners", List.of(), List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_handleMoreThanOneBatchOfIds_inInspector() {
        UUID t = newTenant();
        UUID clinicOwner = asTenant(t, () -> ownerRepository.save(
            Owner.register(t, "Klinik Sahibi", "05550000000", null, null)).getId());
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 1500; i++) {
            ids.add(UUID.randomUUID());
        }
        ids.add(clinicOwner);
        UUID consentId = UUID.randomUUID();
        jdbc.update("INSERT INTO consent_records (id, tenant_id, owner_id, consent_type, granted, granted_at) "
            + "VALUES (?, ?, ?, 'SMS', true, now())", consentId, t, clinicOwner);

        List<Dependent> found = inspector.findForeignDependents("owners", ids, List.of());

        assertThat(found).containsExactly(new Dependent("consent_records", consentId, "owner_id"));
        assertThat(inspector.findForeignDependents("owners", ids, List.of(consentId))).isEmpty();
    }

    @Test
    void should_reject_whenNotCommitted() {
        UUID t = newTenant();
        UUID previewed = previewUseCase.execute(t, SYSTEM, "a.xlsx",
            workbook(List.<String[]>of(owner("S1", "Ayşe", "05321112233")), List.of()), adminId).getId();

        assertThatThrownBy(() -> rollback(t, previewed)).isInstanceOf(ImportStateConflictException.class);
        assertThat(batch(previewed).getStatus()).isEqualTo(ImportBatchStatus.PREVIEWED);

        UUID committed = importFile(t, workbook(List.<String[]>of(owner("S2", "Veli", "05442223344")), List.of()));
        assertThat(rollback(t, committed).rolledBack()).isTrue();
        // ikinci geri alma: ROLLED_BACK -> 409
        assertThatThrownBy(() -> rollback(t, committed)).isInstanceOf(ImportStateConflictException.class);
    }

    @Test
    void should_notFind_batchOfAnotherTenant() {
        UUID t = newTenant();
        UUID other = newTenant();
        UUID batchId = importFile(t, workbook(List.<String[]>of(owner("S1", "Ayşe", "05321112233")), List.of()));

        assertThatThrownBy(() -> rollback(other, batchId)).isInstanceOf(ImportBatchNotFoundException.class);
        assertThat(owners(t)).isEqualTo(1);
        assertThat(batch(batchId).getStatus()).isEqualTo(ImportBatchStatus.COMMITTED);
    }

    @Test
    void should_rollbackFailedBatch_thatStillHasLinks() {
        UUID t = newTenant();
        UUID batchId = importFile(t, workbook(
            List.<String[]>of(owner("S1", "Ayşe Yılmaz", "05321112233")), List.<String[]>of(patient("H1", "S1", "Pamuk"))));
        // Otomatik temizligi basarisiz olmus FAILED parti: kayitlar + linkler duruyor.
        jdbc.update("UPDATE import_batches SET status = 'FAILED' WHERE id = ?", batchId);
        assertThat(batch(batchId).getStatus()).isEqualTo(ImportBatchStatus.FAILED);

        RollbackResult result = rollback(t, batchId);

        assertThat(result.rolledBack()).isTrue();
        assertThat(owners(t)).isZero();
        assertThat(patients(t)).isZero();
        assertThat(links(t)).isZero();
        assertThat(batch(batchId).getStatus()).isEqualTo(ImportBatchStatus.ROLLED_BACK);
    }

    @Test
    void should_reject_failedBatchWithoutLinks() {
        UUID t = newTenant();
        UUID batchId = importFile(t, workbook(List.<String[]>of(owner("S1", "Ayşe", "05321112233")), List.of()));
        jdbc.update("UPDATE import_batches SET status = 'FAILED' WHERE id = ?", batchId);
        jdbc.update("DELETE FROM import_record_links WHERE batch_id = ?", batchId);

        assertThatThrownBy(() -> rollback(t, batchId)).isInstanceOf(ImportStateConflictException.class);
        assertThat(batch(batchId).getStatus()).isEqualTo(ImportBatchStatus.FAILED);
    }

    @Test
    void should_exposeRollbackable_onlyForCommittedOrFailedWithLinks() throws Exception {
        UUID t = newTenant();
        UUID committed = importFile(t, workbook(List.<String[]>of(owner("S1", "Ayşe", "05321112233")), List.of()));
        UUID failedWithLinks = importFile(t, workbook(List.<String[]>of(owner("S2", "Veli", "05442223344")), List.of()));
        jdbc.update("UPDATE import_batches SET status = 'FAILED' WHERE id = ?", failedWithLinks);
        UUID failedClean = importFile(t, workbook(List.<String[]>of(owner("S3", "Can", "05553334455")), List.of()));
        jdbc.update("UPDATE import_batches SET status = 'FAILED' WHERE id = ?", failedClean);
        jdbc.update("DELETE FROM import_record_links WHERE batch_id = ?", failedClean);
        UUID previewed = previewUseCase.execute(t, SYSTEM, "p.xlsx",
            workbook(List.<String[]>of(owner("S4", "Ece", "05001112233")), List.of()), adminId).getId();
        String platform = "Bearer " + platformAdminJwtTokenProvider.generateToken(adminId, ADMIN_EMAIL);
        String base = "/api/v1/platform-admin/tenants/" + t + "/imports";

        mockMvc.perform(get(base).header("Authorization", platform))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '" + committed + "')].rollbackable").value(true))
            .andExpect(jsonPath("$[?(@.id == '" + failedWithLinks + "')].rollbackable").value(true))
            .andExpect(jsonPath("$[?(@.id == '" + failedClean + "')].rollbackable").value(false))
            .andExpect(jsonPath("$[?(@.id == '" + previewed + "')].rollbackable").value(false));
        mockMvc.perform(get(base + "/" + failedWithLinks).header("Authorization", platform))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rollbackable").value(true));
        mockMvc.perform(get(base + "/" + failedClean).header("Authorization", platform))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rollbackable").value(false));
    }

    @Test
    void should_exposeRollbackEndpoint() throws Exception {
        UUID t = newTenant();
        UUID batchId = importFile(t, workbook(
            List.<String[]>of(owner("S1", "Ayşe Yılmaz", "05321112233")), List.<String[]>of(patient("H1", "S1", "Pamuk"))));
        UUID importedOwner = targetOf(t, batchId, ImportEntityType.OWNER, "S1");
        String platform = "Bearer " + platformAdminJwtTokenProvider.generateToken(adminId, ADMIN_EMAIL);
        String clinic = "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), t, List.of(), "ADMIN");
        String url = "/api/v1/platform-admin/tenants/" + t + "/imports/" + batchId + "/rollback";

        mockMvc.perform(post(url).header("Authorization", clinic)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/platform-admin/tenants/" + UUID.randomUUID() + "/imports/" + batchId + "/rollback")
            .header("Authorization", platform)).andExpect(status().isNotFound());

        UUID clinicPatient = clinicPatientFor(t, importedOwner, "Sonradan");
        mockMvc.perform(post(url).header("Authorization", platform))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rolledBack").value(false))
            .andExpect(jsonPath("$.blockers[0].table").value("patients"))
            .andExpect(jsonPath("$.blockers[0].rowId").value(clinicPatient.toString()));

        jdbc.update("DELETE FROM patients WHERE id = ?", clinicPatient);
        mockMvc.perform(post(url).header("Authorization", platform))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rolledBack").value(true))
            .andExpect(jsonPath("$.blockers").isEmpty());
        mockMvc.perform(post(url).header("Authorization", platform)).andExpect(status().isConflict());
        assertThat(Map.of("owners", owners(t), "patients", patients(t))).containsEntry("owners", 0);
    }
}
