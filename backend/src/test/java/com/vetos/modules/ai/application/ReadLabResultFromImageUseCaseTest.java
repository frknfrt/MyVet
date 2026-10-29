package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiTaskType;
import com.vetos.modules.ai.domain.LabResultOcrDraft;
import com.vetos.modules.ai.domain.LabResultOcrPort;
import com.vetos.modules.ai.domain.OcrLabItem;
import com.vetos.modules.ai.domain.exception.AiBusyServiceUnavailableException;
import com.vetos.modules.ai.domain.exception.NoFileSelectedForOcrException;
import com.vetos.modules.ai.domain.exception.UnsupportedOcrFileTypeException;
import com.vetos.modules.lab.domain.LabResultFileForOcr;
import com.vetos.modules.lab.domain.LabResultFileLookupPort;
import com.vetos.modules.lab.domain.LabValueFlag;
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

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29) --
 * Task 5. GenerateDiagnosisSuggestionUseCaseTest ile ayni AiCallGate/UsageQuotaPort
 * mocklama deseni.
 */
@ExtendWith(MockitoExtension.class)
class ReadLabResultFromImageUseCaseTest {

    @Mock private LabResultFileLookupPort labResultFileLookupPort;
    @Mock private LabResultOcrPort labResultOcrPort;
    @Mock private RecordAiJobUseCase recordAiJobUseCase;
    @Mock private UsageQuotaPort quota;

    private ReadLabResultFromImageUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReadLabResultFromImageUseCase(
            labResultFileLookupPort, labResultOcrPort, recordAiJobUseCase, "ollama", quota,
            new AiCallGate(BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(4).build()),
                new TenantRateLimiter(new RateLimitProperties(600, 10, 2000)))
        );
    }

    private LabResultFileForOcr imageFile(UUID id) {
        return new LabResultFileForOcr(id, "rapor.jpg", "image/jpeg", "fake-bytes".getBytes());
    }

    @Test
    void noFilesSelected_throwsBeforeReservingQuota() {
        UUID tenantId = UUID.randomUUID();
        UUID labResultId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.execute(new ReadLabResultFromImageCommand(tenantId, labResultId, List.of(), UUID.randomUUID())))
            .isInstanceOf(NoFileSelectedForOcrException.class);

        verifyNoInteractions(labResultFileLookupPort, labResultOcrPort, recordAiJobUseCase, quota);
    }

    @Test
    void nonImageFile_throwsUnsupportedOcrFileType() {
        UUID tenantId = UUID.randomUUID();
        UUID labResultId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(labResultFileLookupPort.findFiles(labResultId, List.of(fileId)))
            .thenReturn(List.of(new LabResultFileForOcr(fileId, "sonuc.pdf", "application/pdf", "bytes".getBytes())));

        assertThatThrownBy(() -> useCase.execute(new ReadLabResultFromImageCommand(tenantId, labResultId, List.of(fileId), UUID.randomUUID())))
            .isInstanceOf(UnsupportedOcrFileTypeException.class);

        verifyNoInteractions(labResultOcrPort, recordAiJobUseCase, quota);
    }

    @Test
    void execute_feedsOcrReferenceRangeIntoRuleEngine() {
        UUID tenantId = UUID.randomUUID();
        UUID labResultId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(labResultFileLookupPort.findFiles(labResultId, List.of(fileId))).thenReturn(List.of(imageFile(fileId)));
        when(labResultOcrPort.generate(any())).thenReturn(new LabResultOcrDraft(
            List.of(
                new OcrLabItem("WBC", "12.3", "10^3/uL", "6.0-17.0"),
                new OcrLabItem("Glukoz", "180", "mg/dL", "70-120")
            ),
            true, "llava"
        ));
        when(recordAiJobUseCase.execute(any())).thenReturn(UUID.randomUUID());

        ReadLabResultFromImageResult result = useCase.execute(
            new ReadLabResultFromImageCommand(tenantId, labResultId, List.of(fileId), UUID.randomUUID())
        );

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).flag()).isEqualTo(LabValueFlag.NORMAL);
        assertThat(result.items().get(1).flag()).isEqualTo(LabValueFlag.HIGH);
        assertThat(result.draftSummary()).contains("Glukoz").contains("1 parametre");
        assertThat(result.modelConnected()).isTrue();
    }

    @Test
    void recordsAiJobWithNullEncounterId_andLabResultOcrTaskType() {
        UUID tenantId = UUID.randomUUID();
        UUID labResultId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        when(labResultFileLookupPort.findFiles(labResultId, List.of(fileId))).thenReturn(List.of(imageFile(fileId)));
        when(labResultOcrPort.generate(any())).thenReturn(new LabResultOcrDraft(List.of(), true, "llava"));
        when(recordAiJobUseCase.execute(any())).thenReturn(UUID.randomUUID());

        useCase.execute(new ReadLabResultFromImageCommand(tenantId, labResultId, List.of(fileId), staffId));

        ArgumentCaptor<RecordAiJobCommand> captor = ArgumentCaptor.forClass(RecordAiJobCommand.class);
        verify(recordAiJobUseCase).execute(captor.capture());
        assertThat(captor.getValue().encounterId()).isNull();
        assertThat(captor.getValue().taskType()).isEqualTo(AiTaskType.LAB_RESULT_OCR);
        assertThat(captor.getValue().requestedByStaffUserId()).isEqualTo(staffId);
    }

    @Test
    void should_release_when_modelNotConnected() {
        UUID tenantId = UUID.randomUUID();
        UUID labResultId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(labResultFileLookupPort.findFiles(labResultId, List.of(fileId))).thenReturn(List.of(imageFile(fileId)));
        when(labResultOcrPort.generate(any())).thenReturn(new LabResultOcrDraft(List.of(), false, "llava"));
        when(recordAiJobUseCase.execute(any())).thenReturn(UUID.randomUUID());

        ReadLabResultFromImageResult result = useCase.execute(
            new ReadLabResultFromImageCommand(tenantId, labResultId, List.of(fileId), UUID.randomUUID())
        );

        assertThat(result.modelConnected()).isFalse();
        assertThat(result.draftSummary()).contains("okuyamadi");
        verify(quota).release(tenantId, UsageKind.AI, 1);
    }

    @Test
    void should_notReserveQuota_when_bulkheadFull() {
        AiCallGate fullGate = mock(AiCallGate.class);
        when(fullGate.run(any(), any())).thenThrow(new AiBusyServiceUnavailableException());
        UUID labResultId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(labResultFileLookupPort.findFiles(labResultId, List.of(fileId))).thenReturn(List.of(imageFile(fileId)));
        ReadLabResultFromImageUseCase gated = new ReadLabResultFromImageUseCase(
            labResultFileLookupPort, labResultOcrPort, recordAiJobUseCase, "ollama", quota, fullGate
        );

        assertThatThrownBy(() -> gated.execute(new ReadLabResultFromImageCommand(UUID.randomUUID(), labResultId, List.of(fileId), UUID.randomUUID())))
            .isInstanceOf(AiBusyServiceUnavailableException.class);

        verifyNoInteractions(quota, recordAiJobUseCase);
    }
}
