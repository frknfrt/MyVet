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
import com.vetos.platform.security.AuthenticatedStaffUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
            jdbcTemplate.update("DELETE FROM pending_stock_deduction WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM tarbil_submission WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM tarbil_extension_token WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM tarbil_value_mapping WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM tarbil_stock_snapshot_line WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM tarbil_stock_snapshot WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM invoice_lines WHERE invoice_id IN (SELECT id FROM invoices WHERE tenant_id = ?)", t);
            jdbcTemplate.update("DELETE FROM invoices WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM encounter_inventory_usage WHERE encounter_id IN (SELECT id FROM encounters WHERE tenant_id = ?)", t);
            jdbcTemplate.update("DELETE FROM prescription_items WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM prescriptions WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM stock_movements WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM inventory_items WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM vaccination_records WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM encounters WHERE tenant_id = ?", t);
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
    void lastSeenClientVersionIsRecordedAndListed() throws Exception {
        mockMvc.perform(get("/api/v1/tarbil-extension/pending").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk());
        assertThat(listExtensionTokensUseCase.execute(tenantA, staffA, false).get(0).lastClientVersion()).isNull();

        mockMvc.perform(get("/api/v1/tarbil-extension/pending")
                .header("Authorization", "Bearer " + tokenA).header("X-Client-Version", "0.2.0"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tarbil/extension/tokens").header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].lastClientVersion").value("0.2.0"));
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
        // denetim kaydi icin guvenlik baglami gerekir (urun kodu varsayilan aktor uretmez)
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new AuthenticatedStaffUser(staffA, tenantA, List.of(), "VET"), null, List.of()));
        try {
            revokeExtensionTokenUseCase.execute(tenantA, staffA, false, tokenId);
        } finally {
            SecurityContextHolder.clearContext();
        }

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
    void partStockFlagTravelsFromExtensionToTheSyncScreen() throws Exception {
        UUID branchA = inRootSession(() -> branchRepository.save(Branch.create(tenantA, "Parça Şubesi")).getId());
        String jwt = jwtTokenProvider.generateToken(staffA, tenantA, List.of(branchA), "VET");
        // Ilk satir eski eklenti gibi alan gondermez (butun stok); ikincisi "Parca Stok Ara" tablosundan.
        String body = "{\"system\":\"HBSAPP_VACCINE\",\"lines\":["
            + "{\"productName\":\"Biocan DHPPI\",\"lotNumber\":\"145932\",\"quantity\":1},"
            + "{\"productName\":\"Biocan R\",\"lotNumber\":\"665932\",\"quantity\":18,\"partStock\":true}]}";

        mockMvc.perform(post("/api/v1/tarbil-extension/stock-snapshots")
                .header("Authorization", "Bearer " + tokenA).contentType("application/json").content(body))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tarbil/stock-sync?system=HBSAPP_VACCINE").header("Authorization", "Bearer " + jwt))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines[0].productName").value("Biocan DHPPI"))
            .andExpect(jsonPath("$.lines[0].partStock").value(false))
            .andExpect(jsonPath("$.lines[1].productName").value("Biocan R"))
            .andExpect(jsonPath("$.lines[1].partStock").value(true));
    }

    @Test
    void vaccinationFromStockWaitsForTarbilAndCancellationClosesIt() throws Exception {
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
        UUID vid = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        Integer afterRecord = jdbcTemplate.queryForObject("SELECT quantity_on_hand FROM inventory_items WHERE id = ?", Integer.class, item);
        assertThat(afterRecord).isEqualTo(18);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM pending_stock_deduction WHERE reference_id = ?", String.class, vid))
            .isEqualTo("PENDING");

        mockMvc.perform(post(location + "/cancel").header("Authorization", "Bearer " + jwt))
            .andExpect(status().is2xxSuccessful());
        Integer afterCancel = jdbcTemplate.queryForObject("SELECT quantity_on_hand FROM inventory_items WHERE id = ?", Integer.class, item);
        assertThat(afterCancel).isEqualTo(18);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM pending_stock_deduction WHERE reference_id = ?", String.class, vid))
            .isEqualTo("CANCELLED");
    }

    @Test
    void stockSyncBlocksAProductWhoseVaccinationStillWaitsForTarbil() throws Exception {
        UUID branchA = inRootSession(() -> branchRepository.save(Branch.create(tenantA, "Bekleyen Şube")).getId());
        String jwt = jwtTokenProvider.generateToken(staffA, tenantA, List.of(branchA), "VET");
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        UUID owner = asTenant(tenantA, () -> ownerRepository.save(Owner.register(tenantA, "B Sahip", "05550000001", null, null)).getId());
        UUID patient = asTenant(tenantA, () -> patientRepository.save(
            Patient.register(tenantA, owner, speciesId, null, "Boncuk", Sex.FEMALE, null)).getId());
        UUID item = asTenant(tenantA, () -> inventoryItemRepository.save(
            InventoryItem.create(tenantA, branchA, "Biocan DHPPI", "Aşı", null, 5, 0, LocalDate.of(2027, 8, 31), "145932", null)).getId());
        mockMvc.perform(post("/api/v1/vaccination-records").header("Authorization", "Bearer " + jwt).contentType("application/json")
                .content("{\"patientId\":\"" + patient + "\",\"vaccineName\":\"Biocan DHPPI\",\"lotNumber\":\"145932\","
                    + "\"administeredDate\":\"" + LocalDate.now() + "\",\"status\":\"ADMINISTERED\",\"inventoryItemId\":\"" + item + "\"}"))
            .andExpect(status().isCreated());
        // TARBIL'de miktar farkli (ornegin bir kabul Vetly'ye islenmemis); asi ise henuz TARBIL'e gonderilmedi.
        mockMvc.perform(post("/api/v1/tarbil-extension/stock-snapshots").header("Authorization", "Bearer " + tokenA)
                .contentType("application/json")
                .content("{\"system\":\"HBSAPP_VACCINE\",\"lines\":[{\"productName\":\"Biocan DHPPI\",\"lotNumber\":\"145932\",\"quantity\":7}]}"))
            .andExpect(status().isCreated());

        String view = mockMvc.perform(get("/api/v1/tarbil/stock-sync?system=HBSAPP_VACCINE").header("Authorization", "Bearer " + jwt))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines[0].status").value("QUANTITY_DIFFERS"))
            .andExpect(jsonPath("$.lines[0].pendingTarbilCount").value(1))
            .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/v1/tarbil/stock-sync/" + JsonPath.read(view, "$.snapshotId") + "/apply")
                .header("Authorization", "Bearer " + jwt).contentType("application/json")
                .content("{\"lineIds\":[\"" + JsonPath.read(view, "$.lines[0].lineId") + "\"]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("TARBIL_STOCK_LINE_PENDING"));
        assertThat(stockOf(item)).isEqualTo(5);
    }

    /** Stoktan asi kaydeder; asi kaydi id'si ve kalem id'si (spec 2026-10-09 S5.2 testleri). */
    private UUID[] vaccinateFromStock(int stock) throws Exception {
        UUID branchA = inRootSession(() -> branchRepository.save(Branch.create(tenantA, "Aşı Şubesi")).getId());
        String jwt = jwtTokenProvider.generateToken(staffA, tenantA, List.of(branchA), "VET");
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        UUID owner = asTenant(tenantA, () -> ownerRepository.save(Owner.register(tenantA, "A Sahip", "05550000000", null, null)).getId());
        UUID patient = asTenant(tenantA, () -> patientRepository.save(
            Patient.register(tenantA, owner, speciesId, null, "Pamuk", Sex.FEMALE, null)).getId());
        UUID item = asTenant(tenantA, () -> inventoryItemRepository.save(
            InventoryItem.create(tenantA, branchA, "Biocan R", "Aşı", null, stock, 0, LocalDate.of(2027, 1, 31), "665932", null)).getId());
        String body = "{\"patientId\":\"" + patient + "\",\"vaccineName\":\"Biocan R\",\"lotNumber\":\"665932\","
            + "\"administeredDate\":\"" + LocalDate.now() + "\",\"status\":\"ADMINISTERED\",\"inventoryItemId\":\"" + item + "\"}";
        String location = mockMvc.perform(post("/api/v1/vaccination-records").header("Authorization", "Bearer " + jwt)
                .contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getHeader("Location");
        return new UUID[] { UUID.fromString(location.substring(location.lastIndexOf('/') + 1)), item };
    }

    private Integer stockOf(UUID item) {
        return jdbcTemplate.queryForObject("SELECT quantity_on_hand FROM inventory_items WHERE id = ?", Integer.class, item);
    }

    @Test
    void vaccinationStockIsDeductedOnceWhenTarbilConfirms() throws Exception {
        UUID[] v = vaccinateFromStock(18);
        UUID submission = jdbcTemplate.queryForObject("SELECT id FROM tarbil_submission WHERE source_id = ?", UUID.class, v[0]);
        assertThat(stockOf(v[1])).isEqualTo(18);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/tarbil-extension/submissions/" + submission + "/submitted")
                    .header("Authorization", "Bearer " + tokenA).contentType("application/json").content("{\"method\":\"AUTO\"}"))
                .andExpect(status().isOk());
        }

        assertThat(stockOf(v[1])).isEqualTo(17);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM pending_stock_deduction WHERE reference_id = ?", String.class, v[0]))
            .isEqualTo("DEDUCTED");
    }

    @Test
    void tarbilConfirmationAfterVetlyOnlyDoesNotDeductAgain() throws Exception {
        UUID[] v = vaccinateFromStock(18);
        UUID submission = jdbcTemplate.queryForObject("SELECT id FROM tarbil_submission WHERE source_id = ?", UUID.class, v[0]);

        mockMvc.perform(get("/api/v1/inventory/pending-deductions").header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.referenceId == '" + v[0] + "')]").isNotEmpty());

        mockMvc.perform(post("/api/v1/inventory/pending-deductions/VACCINATION/" + v[0] + "/settle-vetly-only")
                .header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isNoContent());
        assertThat(stockOf(v[1])).isEqualTo(17);
        // Asi kaydinin kendisi TARBIL'e bildirilmeli: Vetly'den dusmek aktarimi reddetmez (review 2026-10-10).
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tarbil_submission WHERE id = ?", String.class, submission)).isEqualTo("PENDING");

        mockMvc.perform(post("/api/v1/tarbil-extension/submissions/" + submission + "/submitted")
                .header("Authorization", "Bearer " + tokenA).contentType("application/json").content("{\"method\":\"AUTO\"}"))
            .andExpect(status().isOk());
        assertThat(stockOf(v[1])).isEqualTo(17);
    }

    /** Muayene: TARBIL'e bagli kalem kullanilip kapatilir; [encounterId, itemId, jwt'nin subesi]. */
    private UUID[] finalizedEncounterUsingLinkedItem(int stock, int used) throws Exception {
        UUID branchA = jdbcTemplate.queryForObject("SELECT branch_id FROM staff_users WHERE id = ?", UUID.class, staffA);
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        UUID owner = asTenant(tenantA, () -> ownerRepository.save(Owner.register(tenantA, "M Sahip", "05550000001", null, null)).getId());
        UUID patient = asTenant(tenantA, () -> patientRepository.save(
            Patient.register(tenantA, owner, speciesId, null, "Karabaş", Sex.MALE, null)).getId());
        UUID item = asTenant(tenantA, () -> {
            InventoryItem i = InventoryItem.create(tenantA, branchA, "Synulox", "İlaç", null, stock, 0, LocalDate.of(2027, 5, 1), "SY1", null);
            i.linkTarbil("VETILAC_MEDICINE", "SYNULOX 250 MG", "Kutu");
            return inventoryItemRepository.save(i).getId();
        });
        String location = mockMvc.perform(post("/api/v1/encounters").header("Authorization", "Bearer " + jwtA)
                .contentType("application/json").content("{\"patientId\":\"" + patient + "\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        UUID encounter = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        mockMvc.perform(post("/api/v1/encounters/" + encounter + "/materials").header("Authorization", "Bearer " + jwtA)
                .contentType("application/json").content("{\"inventoryItemId\":\"" + item + "\",\"quantity\":" + used + "}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/encounters/" + encounter + "/finalize").header("Authorization", "Bearer " + jwtA))
            .andExpect(status().is2xxSuccessful());
        return new UUID[] { encounter, item };
    }

    private UUID selectableDisease() {
        return jdbcTemplate.queryForObject("SELECT id FROM tarbil_disease WHERE selectable ORDER BY sort_order LIMIT 1", UUID.class);
    }

    @Test
    void encounterMedicineIsDeductedAfterTarbilConfirmation() throws Exception {
        UUID[] e = finalizedEncounterUsingLinkedItem(10, 2);
        assertThat(stockOf(e[1])).isEqualTo(10);

        mockMvc.perform(get("/api/v1/tarbil/encounters/" + e[0] + "/deduction").header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines[0].status").value("PENDING"))
            .andExpect(jsonPath("$.lines[0].quantity").value(2))
            .andExpect(jsonPath("$.submissionId").doesNotExist());

        String created = mockMvc.perform(post("/api/v1/tarbil/encounters/" + e[0] + "/deduction").header("Authorization", "Bearer " + jwtA)
                .contentType("application/json").content("{\"tarbilDiseaseId\":\"" + selectableDisease() + "\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID submission = UUID.fromString(created.replaceAll(".*\"submissionId\":\"([^\"]+)\".*", "$1"));
        assertThat(jdbcTemplate.queryForObject("SELECT source_kind FROM tarbil_submission WHERE id = ?", String.class, submission)).isEqualTo("ENCOUNTER");

        mockMvc.perform(post("/api/v1/tarbil-extension/submissions/" + submission + "/submitted")
                .header("Authorization", "Bearer " + tokenA).contentType("application/json").content("{\"method\":\"MANUAL\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentType").value("PRESCRIPTION"));
        assertThat(stockOf(e[1])).isEqualTo(8);
    }

    @Test
    void queueWithoutPendingLinesConflicts() throws Exception {
        UUID[] e = finalizedEncounterUsingLinkedItem(10, 1);
        mockMvc.perform(post("/api/v1/inventory/pending-deductions/ENCOUNTER/" + e[0] + "/settle-vetly-only").header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/tarbil/encounters/" + e[0] + "/deduction").header("Authorization", "Bearer " + jwtA)
                .contentType("application/json").content("{\"tarbilDiseaseId\":\"" + selectableDisease() + "\"}"))
            .andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tarbil_submission WHERE source_id = ?", Integer.class, e[0])).isZero();
    }

    @Test
    void foreignEncounterDeductionIsNotFound() throws Exception {
        UUID[] e = finalizedEncounterUsingLinkedItem(10, 1);
        UUID staffB = createStaff(tenantB);
        String jwtB = jwtTokenProvider.generateToken(staffB, tenantB, List.of(), "VET");

        mockMvc.perform(get("/api/v1/tarbil/encounters/" + e[0] + "/deduction").header("Authorization", "Bearer " + jwtB))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/tarbil/encounters/" + e[0] + "/deduction").header("Authorization", "Bearer " + jwtB)
                .contentType("application/json").content("{\"tarbilDiseaseId\":\"" + selectableDisease() + "\"}"))
            .andExpect(status().isNotFound());
    }

    /** Muayenesiz recete, TARBIL'e bagli kalemden stoktan ver; [prescriptionId, itemId]. */
    private UUID[] prescriptionGivingFromStock(int stock, int given) throws Exception {
        UUID branchA = jdbcTemplate.queryForObject("SELECT branch_id FROM staff_users WHERE id = ?", UUID.class, staffA);
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        UUID owner = asTenant(tenantA, () -> ownerRepository.save(Owner.register(tenantA, "R Sahip", "05550000002", null, null)).getId());
        UUID patient = asTenant(tenantA, () -> patientRepository.save(
            Patient.register(tenantA, owner, speciesId, null, "Boncuk", Sex.FEMALE, null)).getId());
        UUID item = asTenant(tenantA, () -> {
            InventoryItem i = InventoryItem.create(tenantA, branchA, "Metacam", "İlaç", null, stock, 0, LocalDate.of(2027, 5, 1), "MC7", null);
            i.linkTarbil("VETILAC_MEDICINE", "METACAM", "Şişe");
            return inventoryItemRepository.save(i).getId();
        });
        String body = "{\"patientId\":\"" + patient + "\",\"controlledSubstance\":false,\"tarbilDiseaseId\":\"" + selectableDisease() + "\","
            + "\"items\":[{\"drugName\":\"Metacam\",\"dosage\":\"1 ml\",\"frequency\":\"1x1\",\"durationDays\":3,\"route\":\"ORAL\","
            + "\"inventoryItemId\":\"" + item + "\",\"stockQuantity\":" + given + "}]}";
        String location = mockMvc.perform(post("/api/v1/prescriptions").header("Authorization", "Bearer " + jwtA)
                .contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return new UUID[] { UUID.fromString(location.substring(location.lastIndexOf('/') + 1)), item };
    }

    @Test
    void prescriptionWithoutEncounterGivesStockAfterTarbilConfirmation() throws Exception {
        UUID[] rx = prescriptionGivingFromStock(10, 2);
        assertThat(stockOf(rx[1])).isEqualTo(10);
        UUID submission = jdbcTemplate.queryForObject(
            "SELECT id FROM tarbil_submission WHERE source_id = ? AND document_type = 'PRESCRIPTION' AND source_kind = 'PRESCRIPTION'", UUID.class, rx[0]);
        mockMvc.perform(get("/api/v1/tarbil-extension/pending").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '" + submission + "')].prescription.lines[0].tarbilProductName").value("METACAM"))
            .andExpect(jsonPath("$[?(@.id == '" + submission + "')].prescription.lines[0].quantity").value(2));

        mockMvc.perform(post("/api/v1/tarbil-extension/submissions/" + submission + "/submitted")
                .header("Authorization", "Bearer " + tokenA).contentType("application/json").content("{\"method\":\"MANUAL\"}"))
            .andExpect(status().is2xxSuccessful());
        assertThat(stockOf(rx[1])).isEqualTo(8);
    }

    @Test
    void stockGivenItemIsListedUnderItsStockName() throws Exception {
        UUID[] rx = prescriptionGivingFromStock(10, 1);

        mockMvc.perform(get("/api/v1/prescriptions/" + rx[0]).header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].drugId").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.items[0].drugName").value("Metacam"));
    }

    @Test
    void cancellingAPrescriptionClosesItsPendingDeductionAndSubmission() throws Exception {
        UUID[] rx = prescriptionGivingFromStock(10, 1);

        mockMvc.perform(post("/api/v1/prescriptions/" + rx[0] + "/cancel").header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/prescriptions/" + rx[0] + "/cancel").header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM pending_stock_deduction WHERE reference_id = ?", String.class, rx[0])).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tarbil_submission WHERE source_id = ?", String.class, rx[0])).isEqualTo("DISMISSED");
        assertThat(stockOf(rx[1])).isEqualTo(10);
    }

    @Test
    void stockGivingPrescriptionWithoutDiagnosisIsRejected() throws Exception {
        String body = "{\"patientId\":\"" + UUID.randomUUID() + "\",\"controlledSubstance\":false,"
            + "\"items\":[{\"drugName\":\"Metacam\",\"dosage\":\"1\",\"frequency\":\"1\",\"durationDays\":1,\"route\":\"ORAL\","
            + "\"inventoryItemId\":\"" + UUID.randomUUID() + "\",\"stockQuantity\":1}]}";
        mockMvc.perform(post("/api/v1/prescriptions").header("Authorization", "Bearer " + jwtA).contentType("application/json").content(body))
            .andExpect(status().isUnprocessableEntity());
    }

    /** Hastali, gecerli teshisli recete govdesi; stok kalemi ve teshis parametre. */
    private String stockPrescriptionBody(UUID inventoryItemId, UUID diseaseId, String drugName) {
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        UUID owner = asTenant(tenantA, () -> ownerRepository.save(Owner.register(tenantA, "V Sahip", "05550000003", null, null)).getId());
        UUID patient = asTenant(tenantA, () -> patientRepository.save(
            Patient.register(tenantA, owner, speciesId, null, "Duman", Sex.MALE, null)).getId());
        return "{\"patientId\":\"" + patient + "\",\"controlledSubstance\":false,\"tarbilDiseaseId\":\"" + diseaseId + "\","
            + "\"items\":[{\"drugName\":\"" + drugName + "\",\"dosage\":\"1\",\"frequency\":\"1\",\"durationDays\":1,\"route\":\"ORAL\","
            + "\"inventoryItemId\":\"" + inventoryItemId + "\",\"stockQuantity\":1}]}";
    }

    @Test
    void prescriptionStockItemMustBeAnExistingTarbilLinkedItemOfThisClinic() throws Exception {
        UUID branchB = inRootSession(() -> branchRepository.save(Branch.create(tenantB, "B Şube")).getId());
        UUID foreignItem = asTenant(tenantB, () -> {
            InventoryItem i = InventoryItem.create(tenantB, branchB, "Metacam", "İlaç", null, 5, 0, null, "MC1", null);
            i.linkTarbil("VETILAC_MEDICINE", "METACAM", "Şişe");
            return inventoryItemRepository.save(i).getId();
        });
        UUID branchA = jdbcTemplate.queryForObject("SELECT branch_id FROM staff_users WHERE id = ?", UUID.class, staffA);
        UUID unlinkedItem = asTenant(tenantA, () -> inventoryItemRepository.save(
            InventoryItem.create(tenantA, branchA, "Gazlı bez", "Sarf", null, 5, 0, null, null, null)).getId());

        for (Map.Entry<UUID, String> item : Map.of(UUID.randomUUID(), "Metacam", foreignItem, "Metacam", unlinkedItem, "Gazlı bez").entrySet()) {
            mockMvc.perform(post("/api/v1/prescriptions").header("Authorization", "Bearer " + jwtA)
                    .contentType("application/json").content(stockPrescriptionBody(item.getKey(), selectableDisease(), item.getValue())))
                .andExpect(status().isUnprocessableEntity());
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM prescriptions WHERE tenant_id = ?", Integer.class, tenantA)).isZero();
    }

    @Test
    void prescriptionTarbilDiseaseMustExist() throws Exception {
        UUID branchA = jdbcTemplate.queryForObject("SELECT branch_id FROM staff_users WHERE id = ?", UUID.class, staffA);
        UUID item = asTenant(tenantA, () -> {
            InventoryItem i = InventoryItem.create(tenantA, branchA, "Metacam", "İlaç", null, 5, 0, null, "MC2", null);
            i.linkTarbil("VETILAC_MEDICINE", "METACAM", "Şişe");
            return inventoryItemRepository.save(i).getId();
        });

        mockMvc.perform(post("/api/v1/prescriptions").header("Authorization", "Bearer " + jwtA)
                .contentType("application/json").content(stockPrescriptionBody(item, UUID.randomUUID(), "Metacam")))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void prescriptionStockItemNameMustMatchTheStockItem() throws Exception {
        UUID branchA = jdbcTemplate.queryForObject("SELECT branch_id FROM staff_users WHERE id = ?", UUID.class, staffA);
        UUID item = asTenant(tenantA, () -> {
            InventoryItem i = InventoryItem.create(tenantA, branchA, "Metacam", "İlaç", null, 5, 0, null, "MC3", null);
            i.linkTarbil("VETILAC_MEDICINE", "METACAM", "Şişe");
            return inventoryItemRepository.save(i).getId();
        });

        mockMvc.perform(post("/api/v1/prescriptions").header("Authorization", "Bearer " + jwtA)
                .contentType("application/json").content(stockPrescriptionBody(item, selectableDisease(), "Amoksisilin")))
            .andExpect(status().isUnprocessableEntity());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM prescriptions WHERE tenant_id = ?", Integer.class, tenantA)).isZero();
    }

    @Test
    void acceptsAStockItemNameWithSurroundingSpaces() throws Exception {
        UUID branchA = jdbcTemplate.queryForObject("SELECT branch_id FROM staff_users WHERE id = ?", UUID.class, staffA);
        UUID item = asTenant(tenantA, () -> {
            InventoryItem i = InventoryItem.create(tenantA, branchA, "Metacam", "İlaç", null, 5, 0, null, "MC4", null);
            i.linkTarbil("VETILAC_MEDICINE", "METACAM", "Şişe");
            return inventoryItemRepository.save(i).getId();
        });

        mockMvc.perform(post("/api/v1/prescriptions").header("Authorization", "Bearer " + jwtA)
                .contentType("application/json").content(stockPrescriptionBody(item, selectableDisease(), " Metacam ")))
            .andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("SELECT drug_name FROM prescription_items WHERE inventory_item_id = ?", String.class, item))
            .isEqualTo("Metacam");
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

