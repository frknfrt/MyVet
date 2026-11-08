package com.vetos;

import com.vetos.modules.consentform.application.ArchiveFormTemplateUseCase;
import com.vetos.modules.consentform.application.CreateFormTemplateUseCase;
import com.vetos.modules.consentform.application.GetFormSourceImageUseCase;
import com.vetos.modules.consentform.application.GetFormTemplateUseCase;
import com.vetos.modules.consentform.application.ListFormTemplatesUseCase;
import com.vetos.modules.consentform.application.UpdateFormTemplateUseCase;
import com.vetos.modules.consentform.domain.FormTemplateContent;
import com.vetos.modules.consentform.domain.FormTemplateStatus;
import com.vetos.modules.consentform.application.dto.FormTemplateDetail;
import com.vetos.modules.consentform.domain.exception.FormTemplateArchivedException;
import org.mockito.ArgumentCaptor;
import com.vetos.modules.consentform.domain.exception.FormTemplateNotFoundException;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rol kontrolleri ve hata eslemeleri. Kiracilar arasi 404'un gercek DB kaniti:
 * FormTemplateTenantIsolationTest. Kaynak gorsel yukleme use case'i GERCEK birakildi
 * (dogrulama depolamadan/DB'den once calisir). Canli docker-compose Postgres gerekir.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FormTemplatesEndpointsSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private CreateFormTemplateUseCase createFormTemplateUseCase;
    @MockitoBean private UpdateFormTemplateUseCase updateFormTemplateUseCase;
    @MockitoBean private ArchiveFormTemplateUseCase archiveFormTemplateUseCase;
    @MockitoBean private ListFormTemplatesUseCase listFormTemplatesUseCase;
    @MockitoBean private GetFormTemplateUseCase getFormTemplateUseCase;
    @MockitoBean private GetFormSourceImageUseCase getFormSourceImageUseCase;

    private String bearer(String role) {
        return "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), UUID.randomUUID(), List.of(), role);
    }

    private static final String BODY = """
        {"content":{"title":"Form","sections":[]}}""";

    @Test
    void should_forbidCreate_forTechnician() throws Exception {
        mockMvc.perform(post("/api/v1/form-templates").header("Authorization", bearer("TECHNICIAN"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_forbidWrites_forVetAndReceptionist() throws Exception {
        for (String role : List.of("VET", "RECEPTIONIST", "OWNER_ACCOUNT")) {
            mockMvc.perform(put("/api/v1/form-templates/" + UUID.randomUUID()).header("Authorization", bearer(role))
                    .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/form-templates/" + UUID.randomUUID() + "/archive")
                    .header("Authorization", bearer(role)))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void should_create_forAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        when(createFormTemplateUseCase.execute(any(), any(), any())).thenReturn(id);

        mockMvc.perform(post("/api/v1/form-templates").header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void should_return422_whenUpdatingArchivedTemplate() throws Exception {
        doThrow(new FormTemplateArchivedException()).when(updateFormTemplateUseCase).execute(any(), any(), any());

        mockMvc.perform(put("/api/v1/form-templates/" + UUID.randomUUID()).header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("FORM_TEMPLATE_ARCHIVED"));
    }

    @Test
    void should_listTemplates_forReceptionist() throws Exception {
        when(listFormTemplatesUseCase.execute(false)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/form-templates").header("Authorization", bearer("RECEPTIONIST")))
            .andExpect(status().isOk());
    }

    @Test
    void should_forbidList_forOwnerAccount() throws Exception {
        mockMvc.perform(get("/api/v1/form-templates").header("Authorization", bearer("OWNER_ACCOUNT")))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_return404_whenTemplateMissingOrOtherTenants() throws Exception {
        UUID id = UUID.randomUUID();
        when(getFormTemplateUseCase.execute(id)).thenThrow(new FormTemplateNotFoundException(id));

        mockMvc.perform(get("/api/v1/form-templates/" + id).header("Authorization", bearer("VET")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("FORM_TEMPLATE_NOT_FOUND"));
    }

    @Test
    void should_forbidSourceImageDownload_forVet() throws Exception {
        mockMvc.perform(get("/api/v1/form-templates/source-images/" + UUID.randomUUID())
                .header("Authorization", bearer("VET")))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_forbidSourceImageUpload_forVet() throws Exception {
        mockMvc.perform(multipart("/api/v1/form-templates/source-images")
                .file(new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2, 3}))
                .header("Authorization", bearer("VET")))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_return422_forNonImageSourceFile() throws Exception {
        mockMvc.perform(multipart("/api/v1/form-templates/source-images")
                .file(new MockMultipartFile("file", "a.png", "image/png", "%PDF-1.4 sahte".getBytes()))
                .header("Authorization", bearer("ADMIN")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_FORM_SOURCE_IMAGE"));
    }

    @Test
    void should_archive_forAdmin_withNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/form-templates/" + id + "/archive")
                .header("Authorization", bearer("ADMIN")))
            .andExpect(status().isNoContent());
        verify(archiveFormTemplateUseCase).execute(any(), org.mockito.ArgumentMatchers.eq(id));
    }

    @Test
    void should_allowListAndDetail_forTechnician() throws Exception {
        UUID id = UUID.randomUUID();
        var content = new FormTemplateContent("Form", List.of());
        when(listFormTemplatesUseCase.execute(false)).thenReturn(List.of());
        when(getFormTemplateUseCase.execute(id)).thenReturn(new FormTemplateDetail(
            id, "Form", FormTemplateStatus.ACTIVE, content, null, "Klinik", "hash"));
        String authorization = bearer("TECHNICIAN");
        mockMvc.perform(get("/api/v1/form-templates").header("Authorization", authorization))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/form-templates/" + id).header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.content.title").value("Form"));
    }

    @Test
    void should_ignoreSeparateTitle_andPassContentTitle() throws Exception {
        when(createFormTemplateUseCase.execute(any(), any(), any())).thenReturn(UUID.randomUUID());
        mockMvc.perform(post("/api/v1/form-templates").header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Ignored title\",\"content\":{\"title\":\"Content title\",\"sections\":[]}}"))
            .andExpect(status().isCreated());
        var content = ArgumentCaptor.forClass(FormTemplateContent.class);
        verify(createFormTemplateUseCase).execute(any(), content.capture(), any());
        assertThat(content.getValue().title()).isEqualTo("Content title");
    }

    @Test
    void should_rejectImageOverDomainLimit_viaEndpoint() throws Exception {
        byte[] bytes = new byte[10 * 1024 * 1024 + 1];
        bytes[0] = (byte) 0x89; bytes[1] = 'P'; bytes[2] = 'N'; bytes[3] = 'G';
        mockMvc.perform(multipart("/api/v1/form-templates/source-images")
                .file(new MockMultipartFile("file", "large.png", "image/png", bytes))
                .header("Authorization", bearer("ADMIN")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_FORM_SOURCE_IMAGE"));
    }
}
