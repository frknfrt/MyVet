package com.vetos;

import com.vetos.platform.security.ClientVersionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Canli docker-compose Postgres gerekir. Faz 7 §1: min-version 0.2.0 iken eklenti zinciri
 * eski/basliksiz eklentiyi 426 ile reddeder; diger zincirler etkilenmez.
 */
@SpringBootTest(properties = "vetly.extension.min-version=0.2.0")
@AutoConfigureMockMvc
class ClientVersionEnforcementIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void should_return426WithStandardBody_when_headerMissing() throws Exception {
        mockMvc.perform(get("/api/v1/tarbil-extension/me"))
            .andExpect(status().is(426))
            .andExpect(header().string(ClientVersionFilter.MIN_HEADER, "0.2.0"))
            .andExpect(jsonPath("$.errorCode").value("CLIENT_UPGRADE_REQUIRED"))
            .andExpect(jsonPath("$.message").value(ClientVersionFilter.MESSAGE))
            .andExpect(jsonPath("$.path").value("/api/v1/tarbil-extension/me"))
            .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void should_reachTokenCheck_when_versionSupported() throws Exception {
        mockMvc.perform(get("/api/v1/tarbil-extension/me").header(ClientVersionFilter.HEADER, "0.2.0"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tarbil-extension/me").header(ClientVersionFilter.HEADER, "0.10.0"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return426_when_headerMalformed() throws Exception {
        mockMvc.perform(get("/api/v1/tarbil-extension/me").header(ClientVersionFilter.HEADER, "abc"))
            .andExpect(status().is(426));
    }

    @Test
    void should_checkPairToo() throws Exception {
        mockMvc.perform(post("/api/v1/tarbil-extension/pair")
                .header(ClientVersionFilter.HEADER, "0.1.0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"AAAA-BBBB\",\"label\":\"PC\"}"))
            .andExpect(status().is(426));
    }

    @Test
    void should_notAffectOtherChains() throws Exception {
        mockMvc.perform(get("/api/v1/patients"))
            .andExpect(status().isUnauthorized());
    }
}
