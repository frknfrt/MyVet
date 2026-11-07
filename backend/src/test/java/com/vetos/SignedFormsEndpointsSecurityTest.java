package com.vetos;

import com.vetos.modules.consentform.application.CreateSignedFormUseCase;
import com.vetos.modules.consentform.application.GetSignatureImageUseCase;
import com.vetos.modules.consentform.application.GetSignedFormUseCase;
import com.vetos.modules.consentform.application.ListSignedFormsUseCase;
import com.vetos.modules.consentform.application.VoidSignedFormUseCase;
import com.vetos.modules.consentform.domain.exception.SignedFormAlreadyVoidedException;
import com.vetos.modules.consentform.domain.exception.SignedFormNotFoundException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rol kontrolleri ve hata eslemeleri (use case'ler mock). Kiracilar arasi 404'un gercek DB kaniti:
 * SignedFormIntegrityTest. Canli docker-compose Postgres gerekir.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SignedFormsEndpointsSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private CreateSignedFormUseCase createSignedFormUseCase;
    @MockitoBean private ListSignedFormsUseCase listSignedFormsUseCase;
    @MockitoBean private GetSignedFormUseCase getSignedFormUseCase;
    @MockitoBean private GetSignatureImageUseCase getSignatureImageUseCase;
    @MockitoBean private VoidSignedFormUseCase voidSignedFormUseCase;

    private String bearer(String role) {
        return "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), UUID.randomUUID(), List.of(), role);
    }

    private static String createBody() {
        return """
            {"templateId":"%s","patientId":"%s","encounterId":null,"values":{"ad":"Pamuk"},
             "signer":{"type":"OWNER","name":null,"nationalId":null,"phone":null},"signaturePngBase64":"iVBORw0KGgo="}
            """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void should_forbidVoid_forTechnician() throws Exception {
        mockMvc.perform(post("/api/v1/signed-forms/" + UUID.randomUUID() + "/void")
                .header("Authorization", bearer("TECHNICIAN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Hata\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_voidForm_forVet() throws Exception {
        mockMvc.perform(post("/api/v1/signed-forms/" + UUID.randomUUID() + "/void")
                .header("Authorization", bearer("VET")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Hata\"}"))
            .andExpect(status().isNoContent());
    }

    @Test
    void should_return400_whenVoidReasonBlank() throws Exception {
        mockMvc.perform(post("/api/v1/signed-forms/" + UUID.randomUUID() + "/void")
                .header("Authorization", bearer("VET")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"  \"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void should_return422_whenAlreadyVoided() throws Exception {
        doThrow(new SignedFormAlreadyVoidedException()).when(voidSignedFormUseCase).execute(any(), any(), any());

        mockMvc.perform(post("/api/v1/signed-forms/" + UUID.randomUUID() + "/void")
                .header("Authorization", bearer("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Hata\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("SIGNED_FORM_ALREADY_VOIDED"));
    }

    @Test
    void should_create_forReceptionist() throws Exception {
        UUID id = UUID.randomUUID();
        when(createSignedFormUseCase.execute(any(), any())).thenReturn(id);

        mockMvc.perform(post("/api/v1/signed-forms").header("Authorization", bearer("RECEPTIONIST"))
                .contentType(MediaType.APPLICATION_JSON).content(createBody()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void should_forbidReads_forOwnerAccount() throws Exception {
        mockMvc.perform(get("/api/v1/signed-forms/" + UUID.randomUUID()).header("Authorization", bearer("OWNER_ACCOUNT")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/signed-forms").header("Authorization", bearer("OWNER_ACCOUNT"))
                .contentType(MediaType.APPLICATION_JSON).content(createBody()))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_return404_whenSignedFormMissingOrOtherTenants() throws Exception {
        UUID id = UUID.randomUUID();
        when(getSignedFormUseCase.execute(id)).thenThrow(new SignedFormNotFoundException(id));

        mockMvc.perform(get("/api/v1/signed-forms/" + id).header("Authorization", bearer("VET")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("SIGNED_FORM_NOT_FOUND"));
    }

    @Test
    void should_return405_forPutPatchDelete() throws Exception {
        String url = "/api/v1/signed-forms/" + UUID.randomUUID();
        mockMvc.perform(put(url).header("Authorization", bearer("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch(url).header("Authorization", bearer("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(url).header("Authorization", bearer("ADMIN")))
            .andExpect(status().isMethodNotAllowed());
    }
}
