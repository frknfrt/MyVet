package com.vetos;

import static org.hamcrest.Matchers.hasItem;
import com.jayway.jsonpath.JsonPath;
import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.integration.tarbil.application.CreatePairingCodeUseCase;
import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.integration.tarbil.application.ListExtensionTokensUseCase;
import com.vetos.modules.integration.tarbil.application.PairExtensionUseCase;
import com.vetos.modules.integration.tarbil.application.RevokeExtensionTokenUseCase;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.modules.patient.domain.Sex;
import com.vetos.modules.patient.domain.SpeciesRepository;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffInvite;
import com.vetos.modules.tenant.domain.StaffInviteRepository;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TenantIsolationTest ile ayni bilincli istisna: canli docker-compose Postgres
 * gerekir (cd backend && docker-compose up -d). Testcontainers KULLANILMIYOR.
 * Dogrular: eklenti anahtari yalniz /api/v1/tarbil-extension/** uclarina erisir,
 * JWT eklenti uclarina erisemez, iptal edilen anahtar reddedilir, A kiracisinin
 * anahtari B kiracisinin kaydini goremez.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TarbilExtensionSecurityIntegrationTest extends TenantScopedTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private StaffUserRepository staffUserRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private SpeciesRepository speciesRepository;
    @Autowired private TarbilSubmissionRepository tarbilSyncLogRepository;
    @Autowired private VaccinationRecordRepository vaccinationRecordRepository;
    @Autowired private CreatePairingCodeUseCase createPairingCodeUseCase;
    @Autowired private PairExtensionUseCase pairExtensionUseCase;
    @Autowired private RevokeExtensionTokenUseCase revokeExtensionTokenUseCase;
    @Autowired private ListExtensionTokensUseCase listExtensionTokensUseCase;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StaffInviteRepository staffInviteRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;

    private UUID tenantA;
    private UUID staffA;
    private UUID tenantB;
    private String tokenA;
    private String jwtA;

    private UUID createTenant(String label) {
        return inRootSession(() -> tenantRepository.save(Tenant.register(label + " " + UUID.randomUUID(), null)).getId());
    }

    private UUID createStaff(UUID tenantId) {
        UUID branchId = inRootSession(() -> branchRepository.save(Branch.create(tenantId, "Merkez")).getId());
        return asTenant(tenantId, () -> staffUserRepository.save(StaffUser.register(
            tenantId, branchId, "Dr. Test", "tarbil-ext-" + UUID.randomUUID() + "@test.local", "hash", StaffRole.VET
        )).getId());
    }

    @BeforeEach
    void setUp() {
        tenantA = createTenant("TARBIL A");
        tenantB = createTenant("TARBIL B");
        staffA = createStaff(tenantA);
        String code = createPairingCodeUseCase.execute(tenantA, staffA).code();
        tokenA = pairExtensionUseCase.execute(code, "Test PC");
        jwtA = jwtTokenProvider.generateToken(staffA, tenantA, List.of(), "VET");
    }

    @AfterEach
    void tearDown() {
        for (UUID t : List.of(tenantA, tenantB)) {
            jdbcTemplate.update("DELETE FROM tarbil_submission WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM tarbil_extension_token WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM tarbil_value_mapping WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM tarbil_stock_snapshot_line WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM tarbil_stock_snapshot WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM stock_movements WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM inventory_items WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM vaccination_records WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM patients WHERE owner_id IN (SELECT id FROM owners WHERE tenant_id = ?)", t);
            jdbcTemplate.update("DELETE FROM owners WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM staff_invites WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM staff_users WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM branches WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", t);
        }
    }

    @Test
    void extensionTokenReachesExtensionEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/tarbil-extension/pending").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk());
    }

    @Test
    void extensionTokenCannotReachOtherApis() throws Exception {
        mockMvc.perform(get("/api/v1/patients").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtCannotReachExtensionEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/tarbil-extension/pending").header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedTokenIsRejected() throws Exception {
        UUID tokenId = listExtensionTokensUseCase.execute(tenantA, staffA, false).get(0).id();
        revokeExtensionTokenUseCase.execute(tenantA, staffA, false, tokenId);

        mockMvc.perform(get("/api/v1/tarbil-extension/pending").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isUnauthorized());
    }

    /** B kiracisinda GERCEK asiyla bekleyen satir -- kiraci filtresi silinirse A'nin anahtari gorebilmeli (bos-dogru degil). */
    private UUID foreignPendingSubmission() {
        UUID staffB = createStaff(tenantB);
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        UUID ownerB = asTenant(tenantB, () -> ownerRepository.save(
            Owner.register(tenantB, "B Sahip", "05551234567", null, null)).getId());
        UUID patientB = asTenant(tenantB, () -> patientRepository.save(
            Patient.register(tenantB, ownerB, speciesId, null, "Tekir", Sex.FEMALE, null)).getId());
        UUID vaccinationB = asTenant(tenantB, () -> vaccinationRecordRepository.save(VaccinationRecord.record(
            tenantB, patientB, null, "Kuduz", null, LocalDate.now(), null, staffB, VaccinationStatus.ADMINISTERED, null)).getId());
        return asTenant(tenantB, () -> tarbilSyncLogRepository.save(
            TarbilSubmission.queueVaccination(tenantB, patientB, vaccinationB)).getId());
    }

    private String tokenFor(UUID tenantId) {
        UUID staff = createStaff(tenantId);
        return pairExtensionUseCase.execute(createPairingCodeUseCase.execute(tenantId, staff).code(), "B PC");
    }

    @Test
    void tokenCannotSeeAnotherTenantsSubmission() throws Exception {
        UUID foreignLogId = foreignPendingSubmission();

        // Kontrol: ayni satir kendi kiracisinin anahtariyla gorunur -- 404 yalniz kiraci filtresinden gelir.
        mockMvc.perform(get("/api/v1/tarbil-extension/submissions/" + foreignLogId).header("Authorization", "Bearer " + tokenFor(tenantB)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tarbil-extension/submissions/" + foreignLogId).header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isNotFound());
    }

    @Test
    void tokenCannotMarkOrDismissAnotherTenantsSubmission() throws Exception {
        UUID foreignLogId = foreignPendingSubmission();

        mockMvc.perform(post("/api/v1/tarbil-extension/submissions/" + foreignLogId + "/submitted")
                .header("Authorization", "Bearer " + tokenA).contentType("application/json").content("{\"method\":\"MANUAL\"}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/tarbil-extension/submissions/" + foreignLogId + "/dismiss")
                .header("Authorization", "Bearer " + tokenA).contentType("application/json").content("{\"reason\":\"x\"}"))
            .andExpect(status().isNotFound());

        TarbilSyncStatus statusAfter = asTenant(tenantB, () -> tarbilSyncLogRepository.findById(foreignLogId).orElseThrow().getStatus());
        assertThat(statusAfter).isEqualTo(TarbilSyncStatus.PENDING);
    }

    @Test
    void learnsMappingForVaccineNameContainingSlash() throws Exception {
        // "DHPPi/L" gibi adlar URL yolunda %2F ister ve StrictHttpFirewall reddeder -- anahtar govdede tasinir.
        mockMvc.perform(put("/api/v1/tarbil-extension/mappings/VACCINE")
                .header("Authorization", "Bearer " + tokenA).contentType("application/json")
                .content("{\"key\":\"Eurican DHPPi/L\",\"fields\":{\"productId\":\"42\"}}"))
            .andExpect(status().isNoContent());

        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM tarbil_value_mapping WHERE tenant_id = ? AND vetly_key = ?", Integer.class,
            tenantA, "eurican dhppi/l");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void tokenOfSuspendedTenantIsRejected() throws Exception {
        inRootSession(() -> {
            Tenant t = tenantRepository.findById(tenantA).orElseThrow();
            t.suspend(com.vetos.modules.tenant.domain.TenantSuspensionReason.OTHER, null);
            return tenantRepository.save(t);
        });

        mockMvc.perform(get("/api/v1/tarbil-extension/pending").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void migratedVaccinationSubmissionIsListedAsBefore() throws Exception {
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        UUID ownerA = asTenant(tenantA, () -> ownerRepository.save(
            Owner.register(tenantA, "A Sahip", "05550000000", null, null)).getId());
        UUID patientA = asTenant(tenantA, () -> patientRepository.save(
            Patient.register(tenantA, ownerA, speciesId, null, "Deneme", Sex.FEMALE, null)).getId());
        UUID vaccinationA = asTenant(tenantA, () -> vaccinationRecordRepository.save(VaccinationRecord.record(
            tenantA, patientA, null, "Karma", null, LocalDate.now(), null, staffA, VaccinationStatus.ADMINISTERED, null)).getId());
        UUID submissionId = asTenant(tenantA, () -> tarbilSyncLogRepository.save(
            TarbilSubmission.queueVaccination(tenantA, patientA, vaccinationA)).getId());

        mockMvc.perform(get("/api/v1/tarbil-extension/pending").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(submissionId.toString()))
            .andExpect(jsonPath("$[0].vaccinationRecordId").value(vaccinationA.toString()))
            .andExpect(jsonPath("$[0].documentType").value("VACCINATION"));
        mockMvc.perform(get("/api/v1/tarbil-extension/pending?type=PRESCRIPTION").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void jwtListsTarbilDiseaseCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/tarbil/diseases").header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(73))
            .andExpect(jsonPath("$[?(@.id == '95860589-0057-42cc-8209-663e1e459594')].path")
                .value(hasItem("SİNDİRİM SİSTEMİ HASTALIKLARI > PARAZİTER HASTALIKLAR")))
            .andExpect(jsonPath("$[0].selectable").value(false));
    }

    @Test
    void extensionTokenCannotListDiseaseCatalogThroughWebApi() throws Exception {
        mockMvc.perform(get("/api/v1/tarbil/diseases").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void integrationsScreenListsVaccinationSubmissionWithSameIdAndStatus() throws Exception {
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        UUID ownerA = asTenant(tenantA, () -> ownerRepository.save(
            Owner.register(tenantA, "A Sahip", "05550000001", null, null)).getId());
        UUID patientA = asTenant(tenantA, () -> patientRepository.save(
            Patient.register(tenantA, ownerA, speciesId, null, "Liste", Sex.MALE, null)).getId());
        UUID vaccinationA = asTenant(tenantA, () -> vaccinationRecordRepository.save(VaccinationRecord.record(
            tenantA, patientA, null, "Kuduz", null, LocalDate.now(), null, staffA, VaccinationStatus.ADMINISTERED, null)).getId());
        UUID submissionId = asTenant(tenantA, () -> tarbilSyncLogRepository.save(
            TarbilSubmission.queueVaccination(tenantA, patientA, vaccinationA)).getId());

        mockMvc.perform(get("/api/v1/tarbil/sync-logs").header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(submissionId.toString()))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
            .andExpect(jsonPath("$[0].vaccineName").value("Kuduz"));
    }

    @Test
    void stockSnapshotUploadedByExtensionCanBeAppliedFromWeb() throws Exception {
        UUID branchA = inRootSession(() -> branchRepository.save(Branch.create(tenantA, "Stok Şubesi")).getId());
        String jwt = jwtTokenProvider.generateToken(staffA, tenantA, List.of(branchA), "VET");
        String body = "{\"system\":\"VETILAC_MEDICINE\",\"lines\":[{\"productName\":\"Test İlaç\",\"presentation\":\"Kutu\","
            + "\"lotNumber\":\"LOT-1\",\"expiryDate\":\"2027-01-31\",\"quantity\":3,\"openedQuantity\":null}]}";

        mockMvc.perform(post("/api/v1/tarbil-extension/stock-snapshots")
                .header("Authorization", "Bearer " + tokenA).contentType("application/json").content(body))
            .andExpect(status().isCreated());

        String view = mockMvc.perform(get("/api/v1/tarbil/stock-sync?system=VETILAC_MEDICINE").header("Authorization", "Bearer " + jwt))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines[0].status").value("NEW"))
            .andReturn().getResponse().getContentAsString();
        String snapshotId = JsonPath.read(view, "$.snapshotId");
        String lineId = JsonPath.read(view, "$.lines[0].lineId");

        mockMvc.perform(post("/api/v1/tarbil/stock-sync/" + snapshotId + "/apply").header("Authorization", "Bearer " + jwt)
                .contentType("application/json").content("{\"lineIds\":[\"" + lineId + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied").value(1));
        mockMvc.perform(get("/api/v1/tarbil/stock-sync?system=VETILAC_MEDICINE").header("Authorization", "Bearer " + jwt))
            .andExpect(jsonPath("$.lines[0].status").value("APPLIED"))
            .andExpect(jsonPath("$.lines[0].vetlyQuantity").value(3));
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM inventory_items WHERE tenant_id = ? AND lot_number = 'LOT-1' AND tarbil_system = 'VETILAC_MEDICINE'",
            Integer.class, tenantA);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void vaccinationFromStockDeductsOneAndCancellationReturnsIt() throws Exception {
        UUID branchA = inRootSession(() -> branchRepository.save(Branch.create(tenantA, "Aşı Şubesi")).getId());
        String jwt = jwtTokenProvider.generateToken(staffA, tenantA, List.of(branchA), "VET");
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        UUID owner = asTenant(tenantA, () -> ownerRepository.save(Owner.register(tenantA, "A Sahip", "05550000000", null, null)).getId());
        UUID patient = asTenant(tenantA, () -> patientRepository.save(
            Patient.register(tenantA, owner, speciesId, null, "Pamuk", Sex.FEMALE, null)).getId());
        UUID item = asTenant(tenantA, () -> inventoryItemRepository.save(
            InventoryItem.create(tenantA, branchA, "Biocan R", "Aşı", null, 18, 0, LocalDate.of(2027, 1, 31), "665932", null)).getId());
        String body = "{\"patientId\":\"" + patient + "\",\"vaccineName\":\"Biocan R\",\"lotNumber\":\"665932\","
            + "\"administeredDate\":\"" + LocalDate.now() + "\",\"status\":\"ADMINISTERED\",\"inventoryItemId\":\"" + item + "\"}";

        String location = mockMvc.perform(post("/api/v1/vaccination-records").header("Authorization", "Bearer " + jwt)
                .contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getHeader("Location");
        Integer afterRecord = jdbcTemplate.queryForObject("SELECT quantity_on_hand FROM inventory_items WHERE id = ?", Integer.class, item);
        assertThat(afterRecord).isEqualTo(17);

        mockMvc.perform(post(location + "/cancel").header("Authorization", "Bearer " + jwt))
            .andExpect(status().is2xxSuccessful());
        Integer afterCancel = jdbcTemplate.queryForObject("SELECT quantity_on_hand FROM inventory_items WHERE id = ?", Integer.class, item);
        assertThat(afterCancel).isEqualTo(18);
    }

    @Test
    void databaseRejectsASecondVaccinationOutMovement() {
        UUID item = asTenant(tenantA, () -> inventoryItemRepository.save(
            InventoryItem.create(tenantA, inRootSession(() -> branchRepository.save(Branch.create(tenantA, "Kilit Şubesi")).getId()),
                "Biocan R", "Aşı", null, 5, 0, null, "L9", null)).getId());
        UUID vaccination = UUID.randomUUID();
        String sql = "INSERT INTO stock_movements (id, tenant_id, inventory_item_id, movement_type, quantity, reference_type, reference_id, created_at)"
            + " VALUES (?, ?, ?, 'OUT', 1, 'VACCINATION', ?, now())";
        jdbcTemplate.update(sql, UUID.randomUUID(), tenantA, item, vaccination);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update(sql, UUID.randomUUID(), tenantA, item, vaccination))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void foreignVaccinationCannotBeAdministeredOrCancelled() throws Exception {
        UUID staffB = createStaff(tenantB);
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        UUID ownerB = asTenant(tenantB, () -> ownerRepository.save(Owner.register(tenantB, "B Sahip", "05551234567", null, null)).getId());
        UUID patientB = asTenant(tenantB, () -> patientRepository.save(
            Patient.register(tenantB, ownerB, speciesId, null, "Tekir", Sex.FEMALE, null)).getId());
        UUID vaccinationB = asTenant(tenantB, () -> vaccinationRecordRepository.save(VaccinationRecord.record(
            tenantB, patientB, null, "Kuduz", null, LocalDate.now(), null, staffB, VaccinationStatus.SCHEDULED, null)).getId());

        mockMvc.perform(post("/api/v1/vaccination-records/" + vaccinationB + "/administer").header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/vaccination-records/" + vaccinationB + "/cancel").header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isNotFound());

        String statusAfter = jdbcTemplate.queryForObject("SELECT status FROM vaccination_records WHERE id = ?", String.class, vaccinationB);
        assertThat(statusAfter).isEqualTo("SCHEDULED");
    }

    private String adminJwtA(UUID branchA) {
        return jwtTokenProvider.generateToken(staffA, tenantA, List.of(branchA), "ADMIN");
    }

    @Test
    void emailTakenInAnotherClinicIsRejectedOnInviteAndCreate() throws Exception {
        UUID branchA = inRootSession(() -> branchRepository.save(Branch.create(tenantA, "Personel Şubesi")).getId());
        UUID branchB = inRootSession(() -> branchRepository.save(Branch.create(tenantB, "B Şubesi")).getId());
        String takenEmail = "kayitli-" + UUID.randomUUID() + "@test.local";
        String pendingEmail = "bekleyen-" + UUID.randomUUID() + "@test.local";
        asTenant(tenantB, () -> staffUserRepository.save(StaffUser.register(tenantB, branchB, "B Hekim", takenEmail, "hash", StaffRole.VET)));
        asTenant(tenantB, () -> staffInviteRepository.save(StaffInvite.create(tenantB, branchB, pendingEmail, "B Aday", StaffRole.VET, null)));
        String jwt = adminJwtA(branchA);

        mockMvc.perform(post("/api/v1/staff-invites").header("Authorization", "Bearer " + jwt).contentType("application/json")
                .content("{\"branchId\":\"" + branchA + "\",\"fullName\":\"Yeni\",\"email\":\"" + takenEmail + "\",\"role\":\"VET\"}"))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/staff-invites").header("Authorization", "Bearer " + jwt).contentType("application/json")
                .content("{\"branchId\":\"" + branchA + "\",\"fullName\":\"Yeni\",\"email\":\"" + pendingEmail + "\",\"role\":\"VET\"}"))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/staff-users").header("Authorization", "Bearer " + jwt).contentType("application/json")
                .content("{\"branchId\":\"" + branchA + "\",\"fullName\":\"Yeni\",\"email\":\"" + takenEmail
                    + "\",\"password\":\"Parola1234\",\"role\":\"VET\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void publicInviteLinkWorksWhileLoggedIntoAnotherClinic() throws Exception {
        UUID branchB = inRootSession(() -> branchRepository.save(Branch.create(tenantB, "B Şubesi")).getId());
        String token = asTenant(tenantB, () -> staffInviteRepository.save(StaffInvite.create(
            tenantB, branchB, "davet-" + UUID.randomUUID() + "@test.local", "B Aday", StaffRole.VET, null)).getToken());

        mockMvc.perform(get("/api/v1/public/staff-invites/" + token).header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isOk());
    }

    @Test
    void anotherClinicsWorkingHoursAndShiftsAreNotReadable() throws Exception {
        UUID branchA = inRootSession(() -> branchRepository.save(Branch.create(tenantA, "A Şubesi")).getId());
        UUID branchB = inRootSession(() -> branchRepository.save(Branch.create(tenantB, "B Şubesi")).getId());
        UUID staffB = asTenant(tenantB, () -> staffUserRepository.save(StaffUser.register(
            tenantB, branchB, "B Hekim", "vardiya-" + UUID.randomUUID() + "@test.local", "hash", StaffRole.VET)).getId());
        String jwt = adminJwtA(branchA);

        mockMvc.perform(get("/api/v1/branches/" + branchB + "/working-hours").header("Authorization", "Bearer " + jwt))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/staff-users/" + staffB + "/shifts").header("Authorization", "Bearer " + jwt))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/branches/" + branchA + "/working-hours").header("Authorization", "Bearer " + jwt))
            .andExpect(status().isOk());
    }

    @Test
    void extensionComparesTarbilStockWithoutSavingAnything() throws Exception {
        UUID branchOfStaff = jdbcTemplate.queryForObject("SELECT branch_id FROM staff_users WHERE id = ?", UUID.class, staffA);
        asTenant(tenantA, () -> inventoryItemRepository.save(
            InventoryItem.create(tenantA, branchOfStaff, "Biocan R", "Aşı", null, 2, 0, null, "665932", null)));
        String body = "{\"system\":\"HBSAPP_VACCINE\",\"lines\":["
            + "{\"productName\":\"Biocan R\",\"presentation\":\"Flakon\",\"lotNumber\":\"665932\",\"expiryDate\":\"2027-01-31\",\"quantity\":2,\"openedQuantity\":null},"
            + "{\"productName\":\"Nobivac\",\"presentation\":\"Flakon\",\"lotNumber\":\"N1\",\"expiryDate\":\"2027-01-31\",\"quantity\":1,\"openedQuantity\":null}]}";

        mockMvc.perform(post("/api/v1/tarbil-extension/stock-snapshots/compare")
                .header("Authorization", "Bearer " + tokenA).contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.newCount").value(1))
            .andExpect(jsonPath("$.quantityDiffersCount").value(0))
            .andExpect(jsonPath("$.matchedCount").value(1));

        Integer snapshots = jdbcTemplate.queryForObject("SELECT count(*) FROM tarbil_stock_snapshot WHERE tenant_id = ?", Integer.class, tenantA);
        assertThat(snapshots).isZero();
    }
}

