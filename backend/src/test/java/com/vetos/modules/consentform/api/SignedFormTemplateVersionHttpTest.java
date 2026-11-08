package com.vetos.modules.consentform.api;

import com.vetos.modules.consentform.application.*;
import com.vetos.modules.consentform.application.dto.CreateSignedFormCommand;
import com.vetos.modules.consentform.domain.exception.FormTemplateChangedConflictException;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.web.method.support.*;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SignedFormTemplateVersionHttpTest {
    private final CreateSignedFormUseCase create = mock(CreateSignedFormUseCase.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var controller = new SignedFormsController(create, mock(ListSignedFormsUseCase.class),
            mock(GetSignedFormUseCase.class), mock(GetSignatureImageUseCase.class),
            mock(VoidSignedFormUseCase.class), mock(RenderSignedFormPdfUseCase.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.getParameterType() == AuthenticatedStaffUser.class;
                }
                public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, WebDataBinderFactory factory) {
                    return new AuthenticatedStaffUser(UUID.randomUUID(), UUID.randomUUID(), List.of(), "ADMIN");
                }
            }).build();
    }

    private String body(String hashField) {
        return """
            {"requestId":"%s","templateId":"%s","patientId":"%s","signer":{"type":"OWNER"},
             "values":{},"signaturePngBase64":"png"%s}
            """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), hashField);
    }

    @Test
    void missingRequestIdIsRejectedBeforeCallingUseCase() throws Exception {
        String payload = body(",\"templateContentHash\":\"opened-hash\"")
            .replaceAll("\"requestId\":\"[^\"]+\",", "");
        mvc.perform(post("/api/v1/signed-forms").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(create);
    }

    @Test
    void missingHashIsRejectedBeforeCallingUseCase() throws Exception {
        mvc.perform(post("/api/v1/signed-forms").contentType(MediaType.APPLICATION_JSON).content(body("")))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(create);
    }

    @Test
    void changedTemplateReturnsConflict() throws Exception {
        when(create.execute(any(), any())).thenThrow(new FormTemplateChangedConflictException());
        mvc.perform(post("/api/v1/signed-forms").contentType(MediaType.APPLICATION_JSON)
                .content(body(",\"templateContentHash\":\"opened-hash\"")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("FORM_TEMPLATE_CHANGED"));
    }

    @Test
    void currentHashIsPassedToUseCase() throws Exception {
        UUID id = UUID.randomUUID();
        when(create.execute(any(), any())).thenReturn(id);
        mvc.perform(post("/api/v1/signed-forms").contentType(MediaType.APPLICATION_JSON)
                .content(body(",\"templateContentHash\":\"opened-hash\"")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id.toString()));
        var command = ArgumentCaptor.forClass(CreateSignedFormCommand.class);
        verify(create).execute(any(), command.capture());
        assertThat(command.getValue().templateContentHash()).isEqualTo("opened-hash");
    }

    @Test
    void reusedKeyWithDifferentContentReturnsConflict() throws Exception {
        when(create.execute(any(), any())).thenThrow(new com.vetos.modules.consentform.domain.exception.SignedFormRequestConflictException());
        mvc.perform(post("/api/v1/signed-forms").contentType(MediaType.APPLICATION_JSON)
                .content(body(",\"templateContentHash\":\"opened-hash\"")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("SIGNED_FORM_REQUEST_CONFLICT"));
    }
}

