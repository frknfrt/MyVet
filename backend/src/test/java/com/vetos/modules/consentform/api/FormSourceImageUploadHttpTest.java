package com.vetos.modules.consentform.api;

import com.vetos.modules.consentform.application.*;
import com.vetos.modules.consentform.domain.FormSourceImage;
import com.vetos.modules.consentform.domain.FormSourceImageRepository;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.storage.FileStoragePort;
import com.vetos.platform.tenancy.TenantContext;
import com.vetos.platform.web.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FormSourceImageUploadHttpTest {
    private static final String PATH = "/api/v1/form-templates/source-images";
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47};
    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final FileStoragePort storage = mock(FileStoragePort.class);
    private final FormSourceImageRepository repository = mock(FormSourceImageRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        TenantContext.set(tenantId);
        var controller = new FormTemplatesController(new UploadFormSourceImageUseCase(repository, storage),
            mock(GetFormSourceImageUseCase.class), mock(CreateFormTemplateUseCase.class),
            mock(UpdateFormTemplateUseCase.class), mock(ArchiveFormTemplateUseCase.class),
            mock(ListFormTemplatesUseCase.class), mock(GetFormTemplateUseCase.class), mock(RenderBlankTemplatePdfUseCase.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.getParameterType() == AuthenticatedStaffUser.class;
                }
                public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, WebDataBinderFactory factory) {
                    return new AuthenticatedStaffUser(staffId, tenantId, List.of(), "ADMIN");
                }
            }).build();
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void readFailureReturnsSafeStandardErrorWithoutStoringAnything() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getName()).thenReturn("file");
        when(file.getBytes()).thenThrow(new IOException("/private/upload-secret.tmp: disk failure"));
        mvc.perform(multipart(PATH).with(request -> {
                ((MockMultipartHttpServletRequest) request).addFile(file);
                return request;
            }))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.path").value(PATH))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(content().string(not(containsString("upload-secret"))))
            .andExpect(content().string(not(containsString("IOException"))));
        verifyNoInteractions(storage, repository);
    }

    @Test
    void storageFailureReturnsSafeErrorWithoutSavingSourceImage() throws Exception {
        when(storage.store(any(), eq("image/png"))).thenThrow(new IllegalStateException("private-storage-secret"));
        mvc.perform(multipart(PATH).file(new MockMultipartFile("file", "image.png", "image/png", PNG)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
            .andExpect(content().string(not(containsString("private-storage-secret"))));
        verifyNoInteractions(repository);
    }

    @Test
    void invalidImageIsValidationErrorWithoutStorageAccess() throws Exception {
        mvc.perform(multipart(PATH).file(new MockMultipartFile("file", "fake.png", "image/png", new byte[]{1, 2, 3})))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_FORM_SOURCE_IMAGE"));
        verifyNoInteractions(storage, repository);
    }

    @Test
    void successfulUploadReturnsIdAndSavesDetectedTypeWithTenantAndStaff() throws Exception {
        UUID id = UUID.randomUUID();
        when(storage.store(any(), eq("image/png"))).thenReturn("stored-ref");
        FormSourceImage saved = mock(FormSourceImage.class);
        when(saved.getId()).thenReturn(id);
        when(repository.save(any())).thenReturn(saved);
        mvc.perform(multipart(PATH).file(new MockMultipartFile("file", "misleading.jpg", "image/jpeg", PNG)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()));
        ArgumentCaptor<FormSourceImage> captor = ArgumentCaptor.forClass(FormSourceImage.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().getUploadedBy()).isEqualTo(staffId);
        assertThat(captor.getValue().getStorageRef()).isEqualTo("stored-ref");
        assertThat(captor.getValue().getContentType()).isEqualTo("image/png");
        verify(storage).store(PNG, "image/png");
    }
}
