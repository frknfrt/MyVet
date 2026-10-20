package com.vetos;

import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditActor;
import com.vetos.platform.audit.AuditRecord;
import com.vetos.platform.audit.JdbcAuditEventStore;
import com.vetos.platform.security.JwtTokenProvider;
import com.vetos.platform.security.PlatformAdminJwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Canli docker-compose Postgres gerekir. */
@SpringBootTest
@AutoConfigureMockMvc
class AuditEventsEndpointTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PlatformAdminJwtTokenProvider platformAdminJwtTokenProvider;
    @Autowired private JdbcAuditEventStore store;

    private String bearer(UUID tenantId, String role) {
        return "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), tenantId, List.of(), role);
    }

    private String platformBearer() {
        return "Bearer " + platformAdminJwtTokenProvider.generateToken(UUID.randomUUID(), "platform@vetly.test");
    }

    private void seed(UUID tenant, AuditAction action) {
        store.insert(UUID.randomUUID(), tenant, Instant.now(), AuditActor.staff(UUID.randomUUID(), "Ayşe Yılmaz", "VET"),
            AuditRecord.of(action, "INVOICE", UUID.randomUUID(), "3f2a9c1e", null), "10.0.0.1", null);
    }

    @Test
    void should_listOwnTenantEvents_forClinicAdmin() throws Exception {
        UUID tenant = UUID.randomUUID();
        seed(tenant, AuditAction.INVOICE_VOIDED);
        mockMvc.perform(get("/api/v1/audit-events").header("Authorization", bearer(tenant, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].action").value("INVOICE_VOIDED"))
            .andExpect(jsonPath("$.items[0].actionLabel").value("Fatura iptal edildi"))
            .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void should_notSeeOtherTenantsEvents() throws Exception {
        UUID mine = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        seed(other, AuditAction.INVOICE_VOIDED);
        mockMvc.perform(get("/api/v1/audit-events").header("Authorization", bearer(mine, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void should_filterByAction() throws Exception {
        UUID tenant = UUID.randomUUID();
        seed(tenant, AuditAction.INVOICE_VOIDED);
        seed(tenant, AuditAction.STAFF_CREATED);
        mockMvc.perform(get("/api/v1/audit-events").param("action", "STAFF_CREATED").header("Authorization", bearer(tenant, "ADMIN")))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].action").value("STAFF_CREATED"));
    }

    @Test
    void should_forbidVet() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events").header("Authorization", bearer(UUID.randomUUID(), "VET")))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_rejectOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events").param("size", "500").header("Authorization", bearer(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void should_rejectUnknownAction() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events").param("action", "NOPE").header("Authorization", bearer(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void should_listActionLabels() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events/actions").header("Authorization", bearer(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(22)))
            .andExpect(jsonPath("$[0].code").exists())
            .andExpect(jsonPath("$[0].label").exists());
    }

    @Test
    void should_listTenantEvents_forPlatformAdmin() throws Exception {
        UUID tenant = UUID.randomUUID();
        seed(tenant, AuditAction.INVOICE_VOIDED);
        mockMvc.perform(get("/api/v1/platform-admin/tenants/" + tenant + "/audit-events").header("Authorization", platformBearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].action").value("INVOICE_VOIDED"));
    }

    @Test
    void should_rejectStaffAdmin_onPlatformEndpoint() throws Exception {
        UUID tenant = UUID.randomUUID();
        int s = mockMvc.perform(get("/api/v1/platform-admin/tenants/" + tenant + "/audit-events").header("Authorization", bearer(tenant, "ADMIN")))
            .andReturn().getResponse().getStatus();
        assertTrue(s == 401 || s == 403, "beklenen 401/403, gelen " + s);
    }

    @Test
    void should_rejectOversizedPage_onPlatformEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/platform-admin/tenants/" + UUID.randomUUID() + "/audit-events").param("size", "500").header("Authorization", platformBearer()))
            .andExpect(status().isBadRequest());
    }
}
