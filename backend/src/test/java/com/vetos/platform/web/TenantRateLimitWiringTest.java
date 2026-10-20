package com.vetos.platform.web;

import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Canli docker-compose Postgres gerekir. Filtrenin JWT'den SONRA calistigini gercek zincirde dogrular. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "vetly.rate-limit.tenant-general-per-minute=5",
    "vetly.rate-limit.tenant-ai-per-minute=2"
})
class TenantRateLimitWiringTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String bearer(UUID tenantId) {
        return "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), tenantId, List.of(), "VET");
    }

    @Test
    void should_return429_after_tenantGeneralLimit() throws Exception {
        String token = bearer(UUID.randomUUID());
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/subscriptions/features").header("Authorization", token))
                .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/subscriptions/features").header("Authorization", token))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"))
            .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void should_limitAiGeneration_perTenant() throws Exception {
        String token = bearer(UUID.randomUUID());
        // Bos govde -> controller'da 400 (AI cagrilmaz); filtre jetonu ondan once harcar.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/ai/diagnosis-suggestions").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/v1/ai/diagnosis-suggestions").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isTooManyRequests());

        String otherTenant = bearer(UUID.randomUUID());
        mockMvc.perform(post("/api/v1/ai/diagnosis-suggestions").header("Authorization", otherTenant)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void should_notLimitAnonymousPublicTraffic_byTenantFilter() throws Exception {
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(get("/api/v1/public/signup/plans")).andExpect(status().is2xxSuccessful());
        }
    }
}
