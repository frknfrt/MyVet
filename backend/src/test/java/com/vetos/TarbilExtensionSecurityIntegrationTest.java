package com.vetos;

import static org.hamcrest.Matchers.hasItem;
import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.integration.tarbil.application.CreatePairingCodeUseCase;
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
            jdbcTemplate.update("DELETE FROM vaccination_records WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM patients WHERE owner_id IN (SELECT id FROM owners WHERE tenant_id = ?)", t);
            jdbcTemplate.update("DELETE FROM owners WHERE tenant_id = ?", t);
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
            t.suspend();
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
}
