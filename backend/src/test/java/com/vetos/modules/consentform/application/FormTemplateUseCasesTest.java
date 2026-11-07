package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.application.dto.FormTemplateSummary;
import com.vetos.modules.consentform.domain.*;
import com.vetos.modules.consentform.domain.exception.*;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import com.vetos.platform.storage.FileStoragePort;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FormTemplateUseCasesTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    @Mock FormTemplateRepository templateRepository;
    @Mock FormSourceImageRepository sourceImageRepository;
    @Mock SignedFormRepository signedFormRepository;
    @Mock FileStoragePort fileStoragePort;
    @Mock TenantLookupPort tenantLookupPort;

    @BeforeEach void setTenant() { TenantContext.set(tenantId); }
    @AfterEach void clearTenant() { TenantContext.clear(); }

    private static FormTemplateContent validContent(String title) {
        return new FormTemplateContent(title, List.of(new FormSection("Bölüm", List.of(
            new FormItem(FormItemType.SHORT_TEXT, "ad", "Ad", null, true, "owner.fullName", null, null, false),
            new FormItem(FormItemType.SIGNATURE, "imza", "İmza", null, true, null, null, null, false)
        ))));
    }

    @Test
    void create_should_validateAndStoreContentAsCodecJson() {
        when(templateRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        FormTemplateContent content = validContent("Anestezi Onamı");

        new CreateFormTemplateUseCase(templateRepository, sourceImageRepository).execute(staffId, content, null);

        ArgumentCaptor<FormTemplate> captor = ArgumentCaptor.forClass(FormTemplate.class);
        verify(templateRepository).save(captor.capture());
        FormTemplate saved = captor.getValue();
        assertThat(saved.getContent()).isEqualTo(FormTemplateContentCodec.toJson(content));
        assertThat(saved.getTitle()).isEqualTo("Anestezi Onamı");
        assertThat(saved.getStatus()).isEqualTo(FormTemplateStatus.ACTIVE);
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getCreatedBy()).isEqualTo(staffId);
    }

    @Test
    void create_should_rejectInvalidContent() {
        FormTemplateContent noSignature = new FormTemplateContent("Form", List.of(new FormSection(null, List.of(
            new FormItem(FormItemType.SHORT_TEXT, "ad", "Ad", null, false, null, null, null, false)))));

        assertThatThrownBy(() -> new CreateFormTemplateUseCase(templateRepository, sourceImageRepository)
            .execute(staffId, noSignature, null)).isInstanceOf(InvalidFormTemplateException.class);
        verify(templateRepository, never()).save(any());
    }

    @Test
    void create_should_rejectUnknownSourceImage() {
        UUID imageId = UUID.randomUUID();
        when(sourceImageRepository.findById(imageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new CreateFormTemplateUseCase(templateRepository, sourceImageRepository)
            .execute(staffId, validContent("Form"), imageId)).isInstanceOf(FormSourceImageNotFoundException.class);
    }

    @Test
    void update_should_deriveTitleFromContent() {
        FormTemplate t = FormTemplate.create(tenantId, "Eski", "{}", null, staffId);
        UUID id = UUID.randomUUID();
        when(templateRepository.findById(id)).thenReturn(Optional.of(t));
        when(templateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        new UpdateFormTemplateUseCase(templateRepository).execute(staffId, id, validContent("Yeni Başlık"));

        assertThat(t.getTitle()).isEqualTo("Yeni Başlık");
        assertThat(t.getContent()).isEqualTo(FormTemplateContentCodec.toJson(validContent("Yeni Başlık")));
    }

    @Test
    void update_should_rejectArchived() {
        FormTemplate t = FormTemplate.create(tenantId, "Eski", "{}", null, staffId);
        t.archive(staffId);
        UUID id = UUID.randomUUID();
        when(templateRepository.findById(id)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> new UpdateFormTemplateUseCase(templateRepository).execute(staffId, id, validContent("X")))
            .isInstanceOf(FormTemplateArchivedException.class);
        verify(templateRepository, never()).save(any());
    }

    @Test
    void archive_should_keepSignedFormsUntouched() {
        FormTemplate t = FormTemplate.create(tenantId, "Form", "{}", null, staffId);
        UUID id = UUID.randomUUID();
        when(templateRepository.findById(id)).thenReturn(Optional.of(t));
        when(templateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        new ArchiveFormTemplateUseCase(templateRepository).execute(staffId, id);

        assertThat(t.getStatus()).isEqualTo(FormTemplateStatus.ARCHIVED);
        assertThat(t.getContent()).isEqualTo("{}");
        verifyNoInteractions(signedFormRepository);
    }

    @Test
    void list_should_countSignedForms() {
        FormTemplate a = FormTemplate.create(tenantId, "A", "{}", UUID.randomUUID(), staffId);
        FormTemplate b = FormTemplate.create(tenantId, "B", "{}", null, staffId);
        when(templateRepository.findByStatusOrderByTitle(FormTemplateStatus.ACTIVE)).thenReturn(List.of(a, b));
        when(signedFormRepository.countByTemplateId(any())).thenReturn(3L, 0L);

        List<FormTemplateSummary> result = new ListFormTemplatesUseCase(templateRepository, signedFormRepository).execute(false);

        assertThat(result).extracting(FormTemplateSummary::title).containsExactly("A", "B");
        assertThat(result).extracting(FormTemplateSummary::signedCount).containsExactly(3L, 0L);
        assertThat(result).extracting(FormTemplateSummary::hasSourceImage).containsExactly(true, false);
    }

    @Test
    void list_should_includeArchived_whenAll() {
        when(templateRepository.findAllByOrderByTitle()).thenReturn(List.of());
        assertThat(new ListFormTemplatesUseCase(templateRepository, signedFormRepository).execute(true)).isEmpty();
        verify(templateRepository, never()).findByStatusOrderByTitle(any());
    }

    @Test
    void get_should_returnClinicName_withFallback() {
        FormTemplate t = FormTemplate.create(tenantId, "Anestezi", FormTemplateContentCodec.toJson(validContent("Anestezi")), null, staffId);
        UUID id = UUID.randomUUID();
        when(templateRepository.findById(id)).thenReturn(Optional.of(t));
        when(tenantLookupPort.findTenantName(tenantId)).thenReturn(Optional.empty());

        var detail = new GetFormTemplateUseCase(templateRepository, tenantLookupPort).execute(id);

        assertThat(detail.clinicName()).isEqualTo("Veteriner Kliniği");
        assertThat(detail.content().title()).isEqualTo("Anestezi");
    }

    @Test
    void get_should_throwNotFound() {
        UUID id = UUID.randomUUID();
        when(templateRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new GetFormTemplateUseCase(templateRepository, tenantLookupPort).execute(id))
            .isInstanceOf(FormTemplateNotFoundException.class);
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @Test
    void upload_should_storeImageWithDetectedType() {
        when(fileStoragePort.store(any(), anyString())).thenReturn("ref-1");
        when(sourceImageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        new UploadFormSourceImageUseCase(sourceImageRepository, fileStoragePort).execute(staffId, PNG);

        verify(fileStoragePort).store(PNG, "image/png");
        ArgumentCaptor<FormSourceImage> captor = ArgumentCaptor.forClass(FormSourceImage.class);
        verify(sourceImageRepository).save(captor.capture());
        assertThat(captor.getValue().getUploadedBy()).isEqualTo(staffId);
        assertThat(captor.getValue().getContentType()).isEqualTo("image/png");
    }

    @Test
    void upload_should_rejectNonImageAndOversize() {
        var useCase = new UploadFormSourceImageUseCase(sourceImageRepository, fileStoragePort);
        assertThatThrownBy(() -> useCase.execute(staffId, "%PDF-1.4 hello".getBytes()))
            .isInstanceOf(UnsupportedFormSourceImageException.class);
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThatThrownBy(() -> useCase.execute(staffId, big)).isInstanceOf(UnsupportedFormSourceImageException.class);
        verifyNoInteractions(fileStoragePort);
    }
}
