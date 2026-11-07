package com.vetos;

import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.ImportEntityType;
import com.vetos.modules.dataimport.domain.ImportRecordLink;
import com.vetos.modules.dataimport.domain.ImportRecordLinkRepository;
import com.vetos.modules.dataimport.domain.ImportReport;
import com.vetos.modules.dataimport.domain.ImportReport.EntityTotals;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import com.vetos.platform.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Canli docker-compose Postgres gerekir. Satirlar @AfterEach'te elle silinir. */
@SpringBootTest
@AutoConfigureMockMvc
class LegacyDataControllerIntegrationTest extends TenantScopedTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ImportBatchRepository batchRepository;
    @Autowired private ImportRecordLinkRepository linkRepository;
    @PersistenceContext private EntityManager entityManager;

    private final List<UUID> createdTenantIds = new ArrayList<>();

    private UUID newTenant() {
        UUID id = inRootSession(() -> tenantRepository.save(Tenant.register("Eski Sistem Testi", null)).getId());
        createdTenantIds.add(id);
        return id;
    }

    private String bearer(UUID tenantId) {
        return "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), tenantId, List.of(), "ADMIN");
    }

    private UUID linkFor(UUID tenantId, ImportEntityType type, Map<String, String> data) {
        ImportReport report = new ImportReport(
            Map.of(ImportEntityType.OWNER, new EntityTotals(1, 1, 0, 0, 0)),
            List.of(), List.of(), List.of(), List.of(), List.of());
        UUID batchId = inRootSession(() -> batchRepository.save(
            ImportBatch.preview(tenantId, "KolayVet", "dosya.xlsx", new byte[]{1}, report, 1, UUID.randomUUID())).getId());
        UUID targetId = UUID.randomUUID();
        asTenantVoid(tenantId, () -> linkRepository.saveAll(List.of(
            ImportRecordLink.of(tenantId, batchId, "KolayVet", type, "17", targetId, true, data))));
        return targetId;
    }

    @AfterEach
    void cleanUp() {
        createdTenantIds.forEach(t -> inRootSessionVoid(() -> {
            for (String sql : List.of(
                "DELETE FROM import_record_links WHERE tenant_id = :t",
                "DELETE FROM import_batches WHERE tenant_id = :t",
                "DELETE FROM tenants WHERE id = :t")) {
                entityManager.createNativeQuery(sql).setParameter("t", t).executeUpdate();
            }
        }));
        createdTenantIds.clear();
    }

    @Test
    void should_returnLegacyDataForOwnTenant() throws Exception {
        UUID tenant = newTenant();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("Bakiye Notu", "Borcu var");
        UUID target = linkFor(tenant, ImportEntityType.OWNER, data);

        mockMvc.perform(get("/api/v1/legacy-data")
                .param("entityType", "OWNER").param("id", target.toString())
                .header("Authorization", bearer(tenant)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceSystem").value("KolayVet"))
            .andExpect(jsonPath("$.sourceId").value("17"))
            .andExpect(jsonPath("$.importedAt").exists())
            .andExpect(jsonPath("$.data['Bakiye Notu']").value("Borcu var"));
    }

    @Test
    void should_return204ForOtherTenantsRecord() throws Exception {
        UUID owner = newTenant();
        UUID other = newTenant();
        UUID target = linkFor(owner, ImportEntityType.OWNER, Map.of("k", "v"));

        mockMvc.perform(get("/api/v1/legacy-data")
                .param("entityType", "OWNER").param("id", target.toString())
                .header("Authorization", bearer(other)))
            .andExpect(status().isNoContent());
    }

    @Test
    void should_return204WhenTypeDiffers_and400ForInvalidType() throws Exception {
        UUID tenant = newTenant();
        UUID target = linkFor(tenant, ImportEntityType.OWNER, Map.of());

        mockMvc.perform(get("/api/v1/legacy-data")
                .param("entityType", "PATIENT").param("id", target.toString())
                .header("Authorization", bearer(tenant)))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/legacy-data")
                .param("entityType", "owner").param("id", target.toString())
                .header("Authorization", bearer(tenant)))
            .andExpect(status().isBadRequest());
    }
}
