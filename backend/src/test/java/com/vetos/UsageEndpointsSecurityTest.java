package com.vetos;

import com.vetos.platform.quota.UsageQuotaPort;
import com.vetos.platform.quota.UsageSnapshot;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Canli docker-compose Postgres gerekir. */
@SpringBootTest
@AutoConfigureMockMvc
class UsageEndpointsSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private UsageQuotaPort usageQuotaPort;

    private String bearer(UUID tenantId, String role) {
        return "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), tenantId, List.of(), role);
    }

    @Test
    void should_returnUsage_forClinicAdmin() throws Exception {
        UUID tenant = UUID.randomUUID();
        when(usageQuotaPort.currentUsage(tenant)).thenReturn(new UsageSnapshot(
            "2026-10", true, new UsageSnapshot.Item(34, 100), new UsageSnapshot.Item(820, null)));

        mockMvc.perform(get("/api/v1/subscriptions/usage").header("Authorization", bearer(tenant, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.period").value("2026-10"))
            .andExpect(jsonPath("$.enforced").value(true))
            .andExpect(jsonPath("$.ai.used").value(34))
            .andExpect(jsonPath("$.ai.quota").value(100))
            .andExpect(jsonPath("$.message.quota").isEmpty());
    }

    @Test
    void should_forbidUsage_forVet() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/usage").header("Authorization", bearer(UUID.randomUUID(), "VET")))
            .andExpect(status().isForbidden());
    }
}
