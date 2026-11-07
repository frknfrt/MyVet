package com.vetos;

import com.vetos.modules.patient.application.GetPatientPhotoUseCase;
import com.vetos.modules.patient.application.RemovePatientPhotoUseCase;
import com.vetos.modules.patient.application.UploadPatientPhotoUseCase;
import com.vetos.modules.patient.application.dto.PatientPhoto;
import com.vetos.modules.patient.domain.exception.PatientPhotoNotFoundException;
import com.vetos.modules.patient.domain.exception.UnsupportedPatientPhotoException;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Canli docker-compose Postgres gerekir. */
@SpringBootTest
@AutoConfigureMockMvc
class PatientPhotoEndpointsSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private UploadPatientPhotoUseCase uploadPatientPhotoUseCase;
    @MockitoBean private RemovePatientPhotoUseCase removePatientPhotoUseCase;
    @MockitoBean private GetPatientPhotoUseCase getPatientPhotoUseCase;

    private String bearer(String role) {
        return "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), UUID.randomUUID(), List.of(), role);
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder put(UUID id) {
        var builder = multipart("/api/v1/patients/" + id + "/photo");
        builder.with(r -> { r.setMethod("PUT"); return r; });
        return builder;
    }

    @Test
    void should_forbidPhotoUpload_forTechnician() throws Exception {
        mockMvc.perform(put(UUID.randomUUID()).file(file()).header("Authorization", bearer("TECHNICIAN")))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_uploadPhoto_forVet() throws Exception {
        UUID id = UUID.randomUUID();
        when(uploadPatientPhotoUseCase.execute(any(), any())).thenReturn("ref-123");

        mockMvc.perform(put(id).file(file()).header("Authorization", bearer("VET")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photoVersion").value("ref-123"));
    }

    @Test
    void should_deletePhoto_forReceptionist() throws Exception {
        mockMvc.perform(delete("/api/v1/patients/" + UUID.randomUUID() + "/photo")
                .header("Authorization", bearer("RECEPTIONIST")))
            .andExpect(status().isNoContent());
    }

    @Test
    void should_servePhoto_forTechnician() throws Exception {
        UUID id = UUID.randomUUID();
        when(getPatientPhotoUseCase.execute(id)).thenReturn(new PatientPhoto(new byte[] {1, 2, 3}, "image/jpeg"));

        mockMvc.perform(get("/api/v1/patients/" + id + "/photo").header("Authorization", bearer("TECHNICIAN")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")));
    }

    @Test
    void should_return404_whenPhotoMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(getPatientPhotoUseCase.execute(id)).thenThrow(new PatientPhotoNotFoundException(id));

        mockMvc.perform(get("/api/v1/patients/" + id + "/photo").header("Authorization", bearer("VET")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("PATIENT_PHOTO_NOT_FOUND"));
    }

    @Test
    void should_return422_forUnsupportedPhoto() throws Exception {
        UUID id = UUID.randomUUID();
        when(uploadPatientPhotoUseCase.execute(any(), any())).thenThrow(new UnsupportedPatientPhotoException());

        mockMvc.perform(put(id).file(file()).header("Authorization", bearer("VET")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_PATIENT_PHOTO"));
    }
}
