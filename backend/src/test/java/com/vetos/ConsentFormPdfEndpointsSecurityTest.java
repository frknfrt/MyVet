package com.vetos;

import com.vetos.modules.consentform.application.RenderBlankTemplatePdfUseCase;
import com.vetos.modules.consentform.application.RenderSignedFormPdfUseCase;
import com.vetos.modules.consentform.application.dto.RenderedConsentPdf;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PDF indirme uc noktalarinin rol kontrolu ve Content-Disposition (use case'ler mock). Canli Postgres gerekir. */
@SpringBootTest
@AutoConfigureMockMvc
class ConsentFormPdfEndpointsSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private RenderBlankTemplatePdfUseCase renderBlankTemplatePdfUseCase;
    @MockitoBean private RenderSignedFormPdfUseCase renderSignedFormPdfUseCase;

    private String bearer(String role) {
        return "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), UUID.randomUUID(), List.of(), role);
    }

    @Test
    void should_forbidPdfs_forOwnerAccount() throws Exception {
        mockMvc.perform(get("/api/v1/form-templates/" + UUID.randomUUID() + "/pdf").header("Authorization", bearer("OWNER_ACCOUNT")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/signed-forms/" + UUID.randomUUID() + "/pdf").header("Authorization", bearer("OWNER_ACCOUNT")))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_returnBlankTemplatePdf_forTechnician() throws Exception {
        UUID id = UUID.randomUUID();
        when(renderBlankTemplatePdfUseCase.execute(id))
            .thenReturn(new RenderedConsentPdf("%PDF-1".getBytes(), "Anestezi Onam Formu", LocalDate.of(2026, 11, 7)));

        mockMvc.perform(get("/api/v1/form-templates/" + id + "/pdf").header("Authorization", bearer("TECHNICIAN")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"Anestezi-Onam-Formu-2026-11-07.pdf\"; filename*=UTF-8''Anestezi%20Onam%20Formu-2026-11-07.pdf"));
    }

    @Test
    void should_returnSignedFormPdf_withAsciiSafeTurkishFilename_forTechnician() throws Exception {
        UUID id = UUID.randomUUID();
        when(renderSignedFormPdfUseCase.execute(id))
            .thenReturn(new RenderedConsentPdf("%PDF-1".getBytes(), "Cerrahi Işlem Onamı", LocalDate.of(2026, 11, 7)));

        mockMvc.perform(get("/api/v1/signed-forms/" + id + "/pdf").header("Authorization", bearer("TECHNICIAN")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.startsWith("attachment; filename=\"Cerrahi-Islem-Onami-2026-11-07.pdf\"; filename*=UTF-8''")));
    }
}
