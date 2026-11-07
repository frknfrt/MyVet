package com.vetos;

import com.vetos.modules.dataimport.application.excel.ImportTemplateWriter;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import com.vetos.platform.security.JwtTokenProvider;
import com.vetos.platform.security.PlatformAdminJwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Canli docker-compose Postgres gerekir. TenantScopedTestSupport paket-ozel oldugu icin com.vetos'ta durur. */
@SpringBootTest
@AutoConfigureMockMvc
class PlatformAdminImportsControllerSecurityTest extends TenantScopedTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PlatformAdminJwtTokenProvider platformAdminJwtTokenProvider;
    @Autowired private TenantRepository tenantRepository;
    @PersistenceContext private EntityManager entityManager;

    private final List<UUID> createdTenantIds = new ArrayList<>();

    private String clinicBearer(UUID tenantId) {
        return "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), tenantId, List.of(), "ADMIN");
    }

    private String platformBearer() {
        return "Bearer " + platformAdminJwtTokenProvider.generateToken(UUID.randomUUID(), "platform@vetly.test");
    }

    private MockMultipartFile templateFile() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ImportTemplateWriter().write(out);
        return new MockMultipartFile("file", "sablon.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    private UUID newTenant() {
        UUID id = inRootSession(() -> tenantRepository.save(Tenant.register("Aktarim Guvenlik", null)).getId());
        createdTenantIds.add(id);
        return id;
    }

    @AfterEach
    void cleanUp() {
        createdTenantIds.forEach(t -> inRootSessionVoid(() -> {
            for (String sql : List.of(
                "DELETE FROM import_record_links WHERE tenant_id = :t",
                "DELETE FROM patients WHERE tenant_id = :t",
                "DELETE FROM owners WHERE tenant_id = :t",
                "DELETE FROM import_batches WHERE tenant_id = :t",
                "DELETE FROM audit_log_entries WHERE target_id = :t",
                "DELETE FROM tenants WHERE id = :t")) {
                entityManager.createNativeQuery(sql).setParameter("t", t).executeUpdate();
            }
        }));
        createdTenantIds.clear();
    }

    @Test
    void should_denyClinicStaffToken() throws Exception {
        UUID tenant = UUID.randomUUID();
        mockMvc.perform(multipart("/api/v1/platform-admin/tenants/{id}/imports", tenant)
                .file(templateFile()).param("sourceSystem", "KolayVet")
                .header("Authorization", clinicBearer(tenant)))
            // Platform-admin zinciri klinik token'ini hic kimlik dogrulamaz (token turu "platform-admin" degil): 401.
            .andExpect(status().isUnauthorized());
    }

    @Test
    void should_rejectUnauthenticated() throws Exception {
        mockMvc.perform(multipart("/api/v1/platform-admin/tenants/{id}/imports", UUID.randomUUID())
                .file(templateFile()).param("sourceSystem", "KolayVet"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void should_createPreview_forPlatformAdmin_thenListGetDiscard() throws Exception {
        UUID tenant = newTenant();
        String body = mockMvc.perform(multipart("/api/v1/platform-admin/tenants/{id}/imports", tenant)
                .file(templateFile()).param("sourceSystem", "KolayVet")
                .header("Authorization", platformBearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PREVIEWED"))
            .andExpect(jsonPath("$.sourceFile").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        String batchId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/v1/platform-admin/tenants/{id}/imports", tenant).header("Authorization", platformBearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(batchId));
        mockMvc.perform(get("/api/v1/platform-admin/tenants/{id}/imports/{b}", tenant, batchId).header("Authorization", platformBearer()))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/platform-admin/tenants/{id}/imports/{b}/discard", tenant, batchId).header("Authorization", platformBearer()))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/platform-admin/tenants/{id}/imports/{b}/discard", tenant, batchId).header("Authorization", platformBearer()))
            .andExpect(status().isConflict());
    }

    @Test
    void should_return404_forUnknownTenant_and400_forGarbageFile() throws Exception {
        mockMvc.perform(multipart("/api/v1/platform-admin/tenants/{id}/imports", UUID.randomUUID())
                .file(templateFile()).param("sourceSystem", "KolayVet")
                .header("Authorization", platformBearer()))
            .andExpect(status().isNotFound());

        UUID tenant = newTenant();
        mockMvc.perform(multipart("/api/v1/platform-admin/tenants/{id}/imports", tenant)
                .file(new MockMultipartFile("file", "x.xlsx", "application/octet-stream", new byte[]{1, 2, 3}))
                .param("sourceSystem", "KolayVet").header("Authorization", platformBearer()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void should_return400_forBlankSourceSystem() throws Exception {
        UUID tenant = newTenant();
        mockMvc.perform(multipart("/api/v1/platform-admin/tenants/{id}/imports", tenant)
                .file(templateFile()).param("sourceSystem", "  ")
                .header("Authorization", platformBearer()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_IMPORT_REQUEST"));
    }

    private String createPreview(UUID tenant) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/platform-admin/tenants/{id}/imports", tenant)
                .file(templateFile()).param("sourceSystem", "KolayVet")
                .header("Authorization", platformBearer()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    @Test
    void should_startCommit_withAccepted_andFinishInBackground() throws Exception {
        UUID tenant = newTenant();
        String batchId = createPreview(tenant);

        mockMvc.perform(post("/api/v1/platform-admin/tenants/{id}/imports/{b}/commit", tenant, batchId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"rejectedMatchSourceIds\": []}")
                .header("Authorization", platformBearer()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("COMMITTING"));

        // Arka plan iscisinin bitmesini bekle (temizlik yarida kalmasin).
        String finalStatus = "COMMITTING";
        for (int i = 0; i < 100 && "COMMITTING".equals(finalStatus); i++) {
            Thread.sleep(200);
            String body = mockMvc.perform(get("/api/v1/platform-admin/tenants/{id}/imports/{b}", tenant, batchId)
                    .header("Authorization", platformBearer()))
                .andReturn().getResponse().getContentAsString();
            finalStatus = com.jayway.jsonpath.JsonPath.read(body, "$.status");
        }
        org.assertj.core.api.Assertions.assertThat(finalStatus).isEqualTo("COMMITTED");

        // Ikinci kez baslatma: 409.
        mockMvc.perform(post("/api/v1/platform-admin/tenants/{id}/imports/{b}/commit", tenant, batchId)
                .header("Authorization", platformBearer()))
            .andExpect(status().isConflict());
    }

    @Test
    void should_return404_whenCommittingBatchOfAnotherTenant() throws Exception {
        UUID tenant = newTenant();
        UUID other = newTenant();
        String batchId = createPreview(tenant);

        mockMvc.perform(post("/api/v1/platform-admin/tenants/{id}/imports/{b}/commit", other, batchId)
                .header("Authorization", platformBearer()))
            .andExpect(status().isNotFound());
    }

    @Test
    void should_downloadTemplate() throws Exception {
        mockMvc.perform(get("/api/v1/platform-admin/imports/template.xlsx").header("Authorization", platformBearer()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"vetly-aktarim-sablonu.xlsx\""));
    }
}
