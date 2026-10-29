package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiTaskType;
import com.vetos.modules.ai.domain.ImagingFindingsOcrDraft;
import com.vetos.modules.ai.domain.ImagingFindingsOcrPort;
import com.vetos.modules.ai.domain.exception.AiBusyServiceUnavailableException;
import com.vetos.modules.ai.domain.exception.NoFileSelectedForOcrException;
import com.vetos.modules.ai.domain.exception.UnsupportedOcrFileTypeException;
import com.vetos.modules.imaging.domain.ImagingRecordFileForOcr;
import com.vetos.modules.imaging.domain.ImagingRecordFileLookupPort;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import com.vetos.platform.ratelimit.RateLimitProperties;
import com.vetos.platform.ratelimit.TenantRateLimiter;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29) -- Task 5. */
@ExtendWith(MockitoExtension.class)
class ReadImagingFindingsFromImageUseCaseTest {

    @Mock private ImagingRecordFileLookupPort imagingRecordFileLookupPort;
    @Mock private ImagingFindingsOcrPort imagingFindingsOcrPort;
    @Mock private RecordAiJobUseCase recordAiJobUseCase;
    @Mock private UsageQuotaPort quota;

    private ReadImagingFindingsFromImageUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReadImagingFindingsFromImageUseCase(
            imagingRecordFileLookupPort, imagingFindingsOcrPort, recordAiJobUseCase, "ollama", quota,
            new AiCallGate(BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(4).build()),
                new TenantRateLimiter(new RateLimitProperties(600, 10, 2000)))
        );
    }

    private ImagingRecordFileForOcr imageFile(UUID id) {
        return new ImagingRecordFileForOcr(id, "rapor.jpg", "image/jpeg", "fake-bytes".getBytes());
    }

    @Test
    void noFilesSelected_throwsBeforeReservingQuota() {
        UUID tenantId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.execute(new ReadImagingFindingsFromImageCommand(tenantId, recordId, List.of(), UUID.randomUUID())))
            .isInstanceOf(NoFileSelectedForOcrException.class);

        verifyNoInteractions(imagingRecordFileLookupPort, imagingFindingsOcrPort, recordAiJobUseCase, quota);
    }

    @Test
    void nonImageFile_throwsUnsupportedOcrFileType() {
        UUID tenantId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(imagingRecordFileLookupPort.findFiles(recordId, List.of(fileId)))
            .thenReturn(List.of(new ImagingRecordFileForOcr(fileId, "tarama.dcm", "application/dicom", "bytes".getBytes())));

        assertThatThrownBy(() -> useCase.execute(new ReadImagingFindingsFromImageCommand(tenantId, recordId, List.of(fileId), UUID.randomUUID())))
            .isInstanceOf(UnsupportedOcrFileTypeException.class);

        verifyNoInteractions(imagingFindingsOcrPort, recordAiJobUseCase, quota);
    }

    @Test
    void execute_returnsFindingsAndRecordsJobWithNullEncounterId() {
        UUID tenantId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        when(imagingRecordFileLookupPort.findFiles(recordId, List.of(fileId))).thenReturn(List.of(imageFile(fileId)));
        when(imagingFindingsOcrPort.generate(any())).thenReturn(new ImagingFindingsOcrDraft("Sol akciger alt lobda hafif infiltrasyon.", true, "llava"));
        when(recordAiJobUseCase.execute(any())).thenReturn(UUID.randomUUID());

        ReadImagingFindingsFromImageResult result = useCase.execute(
            new ReadImagingFindingsFromImageCommand(tenantId, recordId, List.of(fileId), staffId)
        );

        assertThat(result.findings()).isEqualTo("Sol akciger alt lobda hafif infiltrasyon.");
        assertThat(result.modelConnected()).isTrue();

        ArgumentCaptor<RecordAiJobCommand> captor = ArgumentCaptor.forClass(RecordAiJobCommand.class);
        verify(recordAiJobUseCase).execute(captor.capture());
        assertThat(captor.getValue().encounterId()).isNull();
        assertThat(captor.getValue().taskType()).isEqualTo(AiTaskType.IMAGING_FINDINGS_OCR);
    }

    @Test
    void should_release_when_modelNotConnected() {
        UUID tenantId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(imagingRecordFileLookupPort.findFiles(recordId, List.of(fileId))).thenReturn(List.of(imageFile(fileId)));
        when(imagingFindingsOcrPort.generate(any())).thenReturn(new ImagingFindingsOcrDraft("", false, "llava"));
        when(recordAiJobUseCase.execute(any())).thenReturn(UUID.randomUUID());

        ReadImagingFindingsFromImageResult result = useCase.execute(
            new ReadImagingFindingsFromImageCommand(tenantId, recordId, List.of(fileId), UUID.randomUUID())
        );

        assertThat(result.modelConnected()).isFalse();
        verify(quota).release(tenantId, UsageKind.AI, 1);
    }

    @Test
    void should_notReserveQuota_when_bulkheadFull() {
        AiCallGate fullGate = mock(AiCallGate.class);
        when(fullGate.run(any(), any())).thenThrow(new AiBusyServiceUnavailableException());
        UUID recordId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(imagingRecordFileLookupPort.findFiles(recordId, List.of(fileId))).thenReturn(List.of(imageFile(fileId)));
        ReadImagingFindingsFromImageUseCase gated = new ReadImagingFindingsFromImageUseCase(
            imagingRecordFileLookupPort, imagingFindingsOcrPort, recordAiJobUseCase, "ollama", quota, fullGate
        );

        assertThatThrownBy(() -> gated.execute(new ReadImagingFindingsFromImageCommand(UUID.randomUUID(), recordId, List.of(fileId), UUID.randomUUID())))
            .isInstanceOf(AiBusyServiceUnavailableException.class);

        verifyNoInteractions(quota, recordAiJobUseCase);
    }
}
