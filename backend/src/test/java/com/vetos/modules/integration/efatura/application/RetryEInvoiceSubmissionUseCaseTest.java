package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceDocumentType;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import com.vetos.modules.integration.efatura.domain.exception.EInvoiceSubmissionAlreadyProcessingException;
import com.vetos.modules.integration.efatura.domain.exception.EInvoiceSubmissionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryEInvoiceSubmissionUseCaseTest {

    @Mock private EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    @Mock private EInvoiceSubmissionExecutor eInvoiceSubmissionExecutor;

    private RetryEInvoiceSubmissionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RetryEInvoiceSubmissionUseCase(eInvoiceSubmissionRepository, eInvoiceSubmissionExecutor);
    }

    @Test
    void should_requeueAndReattempt_when_submissionFailed() {
        UUID tenantId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        EInvoiceSubmission submission = EInvoiceSubmission.queue(
            tenantId, UUID.randomUUID(), UUID.randomUUID(), EInvoiceDocumentType.E_ARSIV,
            new BigDecimal("120.00"), new BigDecimal("20.00")
        );
        submission.markFailed("onceki hata mesaji", null);
        when(eInvoiceSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        useCase.execute(tenantId, submissionId);

        assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.PENDING);
        verify(eInvoiceSubmissionRepository).save(submission);
        verify(eInvoiceSubmissionExecutor).attemptSubmit(submissionId);
    }

    @Test
    void should_throwAlreadyProcessing_when_submissionAwaitingProviderCallback() {
        UUID tenantId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        EInvoiceSubmission submission = EInvoiceSubmission.queue(
            tenantId, UUID.randomUUID(), UUID.randomUUID(), EInvoiceDocumentType.E_ARSIV,
            new BigDecimal("120.00"), new BigDecimal("20.00")
        );
        submission.markAcceptedByProvider("123");
        when(eInvoiceSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> useCase.execute(tenantId, submissionId))
            .isInstanceOf(EInvoiceSubmissionAlreadyProcessingException.class);

        verify(eInvoiceSubmissionRepository, never()).save(any());
        verifyNoInteractions(eInvoiceSubmissionExecutor);
    }

    @Test
    void should_throwNotFound_when_submissionBelongsToAnotherTenant() {
        UUID callerTenantId = UUID.randomUUID();
        UUID foreignTenantId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        EInvoiceSubmission submission = EInvoiceSubmission.queue(
            foreignTenantId, UUID.randomUUID(), UUID.randomUUID(), EInvoiceDocumentType.E_ARSIV,
            new BigDecimal("120.00"), new BigDecimal("20.00")
        );
        submission.markFailed("hata", null);
        when(eInvoiceSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> useCase.execute(callerTenantId, submissionId))
            .isInstanceOf(EInvoiceSubmissionNotFoundException.class);

        verify(eInvoiceSubmissionRepository, never()).save(any());
        verifyNoInteractions(eInvoiceSubmissionExecutor);
    }
}
