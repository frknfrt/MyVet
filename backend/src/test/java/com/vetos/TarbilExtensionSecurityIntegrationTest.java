package com.vetos;

import com.vetos.modules.integration.tarbil.application.CreatePairingCodeUseCase;
import com.vetos.modules.integration.tarbil.application.ListExtensionTokensUseCase;
import com.vetos.modules.integration.tarbil.application.PairExtensionUseCase;
import com.vetos.modules.integration.tarbil.application.RevokeExtensionTokenUseCase;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
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

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @Autowired private TarbilSyncLogRepository tarbilSyncLogRepository;
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
            jdbcTemplate.update("DELETE FROM tarbil_sync_log WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM tarbil_extension_token WHERE tenant_id = ?", t);
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

    @Test
    void tokenCannotSeeAnotherTenantsSubmission() throws Exception {
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        UUID ownerB = asTenant(tenantB, () -> ownerRepository.save(
            Owner.register(tenantB, "B Sahip", "05551234567", null, null)).getId());
        UUID patientB = asTenant(tenantB, () -> patientRepository.save(
            Patient.register(tenantB, ownerB, speciesId, null, "Tekir", Sex.FEMALE, null)).getId());
        UUID foreignLogId = asTenant(tenantB, () -> tarbilSyncLogRepository.save(
            TarbilSyncLog.queueVaccination(tenantB, patientB, UUID.randomUUID())).getId());

        mockMvc.perform(get("/api/v1/tarbil-extension/submissions/" + foreignLogId).header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isNotFound());
    }
}
