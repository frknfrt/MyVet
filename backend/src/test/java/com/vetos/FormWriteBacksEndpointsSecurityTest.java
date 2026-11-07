package com.vetos;

import com.vetos.modules.patient.application.ApplyFormWriteBacksUseCase;
import com.vetos.modules.patient.application.dto.FormWriteBackResult;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Canli docker-compose Postgres gerekir. */
@SpringBootTest
@AutoConfigureMockMvc
class FormWriteBacksEndpointsSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private ApplyFormWriteBacksUseCase applyFormWriteBacksUseCase;

    private String bearer(String role) {
        return "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), UUID.randomUUID(), List.of(), role);
    }

    private static String body() {
        return "{\"ownerId\":\"" + UUID.randomUUID() + "\",\"patientId\":\"" + UUID.randomUUID()
            + "\",\"signedFormId\":\"" + UUID.randomUUID()
            + "\",\"items\":[{\"binding\":\"owner.phone\",\"value\":\"5551112233\"}]}";
    }

    @Test
    void should_apply_forTechnician() throws Exception {
        when(applyFormWriteBacksUseCase.execute(any()))
            .thenReturn(new FormWriteBackResult(List.of("owner.phone"), List.of()));

        mockMvc.perform(post("/api/v1/form-write-backs").header("Authorization", bearer("TECHNICIAN"))
                .contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied[0]").value("owner.phone"));
    }

    @Test
    void should_forbid_forOwnerAccount() throws Exception {
        mockMvc.perform(post("/api/v1/form-write-backs").header("Authorization", bearer("OWNER_ACCOUNT"))
                .contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_return401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/form-write-backs")
                .contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isUnauthorized());
    }
}
