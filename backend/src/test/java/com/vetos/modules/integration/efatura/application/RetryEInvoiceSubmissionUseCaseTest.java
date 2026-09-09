package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceDocumentType;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import com.vetos.modules.integration.efatura.domain.exception.EInvoiceSubmissionAlreadyProcessingException;
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

    private EInvoiceSubmission aFailedSubmission() {
        EInvoiceSubmission submission = EInvoiceSubmission.queue(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EInvoiceDocumentType.E_ARSIV,
            new BigDecimal("120.00"), new BigDecimal("20.00")
        );
        submission.markFailed("onceki hata mesaji");
        return submission;
    }

    @Test
    void should_requeueAndReattempt_when_submissionFailed() {
        UUID submissionId = UUID.randomUUID();
        EInvoiceSubmission submission = aFailedSubmission();
        when(eInvoiceSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        useCase.execute(submissionId);

        assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.PENDING);
        verify(eInvoiceSubmissionRepository).save(submission);
        verify(eInvoiceSubmissionExecutor).attemptSubmit(submissionId);
    }

    @Test
    void should_throwAlreadyProcessing_when_submissionAwaitingProviderCallback() {
        UUID submissionId = UUID.randomUUID();
        EInvoiceSubmission submission = EInvoiceSubmission.queue(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EInvoiceDocumentType.E_ARSIV,
            new BigDecimal("120.00"), new BigDecimal("20.00")
        );
        submission.markAcceptedByProvider("123");
        when(eInvoiceSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> useCase.execute(submissionId))
            .isInstanceOf(EInvoiceSubmissionAlreadyProcessingException.class);

        verify(eInvoiceSubmissionRepository, never()).save(any());
        verifyNoInteractions(eInvoiceSubmissionExecutor);
    }
}
