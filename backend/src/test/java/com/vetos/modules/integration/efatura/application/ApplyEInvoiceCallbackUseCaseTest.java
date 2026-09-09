package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.billing.domain.InvoiceEInvoiceUpdatePort;
import com.vetos.modules.integration.efatura.domain.EInvoiceDocumentType;
import com.vetos.modules.integration.efatura.domain.EInvoiceGatewayPort;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionOutcome;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplyEInvoiceCallbackUseCaseTest {

    @Mock private EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    @Mock private EInvoiceGatewayPort eInvoiceGatewayPort;
    @Mock private InvoiceEInvoiceUpdatePort invoiceEInvoiceUpdatePort;

    private ApplyEInvoiceCallbackUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ApplyEInvoiceCallbackUseCase(eInvoiceSubmissionRepository, eInvoiceGatewayPort, invoiceEInvoiceUpdatePort);
    }

    private EInvoiceSubmission aProcessingSubmission(UUID invoiceId, String providerReference) {
        EInvoiceSubmission submission = EInvoiceSubmission.queue(
            UUID.randomUUID(), invoiceId, UUID.randomUUID(), EInvoiceDocumentType.E_ARSIV,
            new BigDecimal("120.00"), new BigDecimal("20.00")
        );
        submission.markAcceptedByProvider(providerReference);
        return submission;
    }

    @Test
    void should_markSubmittedAndUpdateInvoice_when_statusIsFinalizedSuccessfully() {
        UUID invoiceId = UUID.randomUUID();
        EInvoiceSubmission submission = aProcessingSubmission(invoiceId, "123");
        when(eInvoiceSubmissionRepository.findByProviderReference("123")).thenReturn(Optional.of(submission));
        when(eInvoiceGatewayPort.fetchStatus("123")).thenReturn(EInvoiceSubmissionOutcome.success("ETTN-XYZ-789", "GIB resmilesmesi tamamlandi"));

        useCase.execute("123");

        assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.SUBMITTED);
        assertThat(submission.getGibReference()).isEqualTo("ETTN-XYZ-789");
        verify(invoiceEInvoiceUpdatePort).applyEInvoiceReference(invoiceId, "ETTN-XYZ-789");
        verify(eInvoiceSubmissionRepository).save(submission);
    }

    @Test
    void should_markFailed_when_statusReportsFailure() {
        EInvoiceSubmission submission = aProcessingSubmission(UUID.randomUUID(), "123");
        when(eInvoiceSubmissionRepository.findByProviderReference("123")).thenReturn(Optional.of(submission));
        when(eInvoiceGatewayPort.fetchStatus("123")).thenReturn(EInvoiceSubmissionOutcome.failure("GIB reddetti"));

        useCase.execute("123");

        assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.FAILED);
        assertThat(submission.getFailureReason()).isEqualTo("GIB reddetti");
        verifyNoInteractions(invoiceEInvoiceUpdatePort);
        verify(eInvoiceSubmissionRepository).save(submission);
    }

    @Test
    void should_leaveProcessing_when_statusStillPending() {
        EInvoiceSubmission submission = aProcessingSubmission(UUID.randomUUID(), "123");
        when(eInvoiceSubmissionRepository.findByProviderReference("123")).thenReturn(Optional.of(submission));
        when(eInvoiceGatewayPort.fetchStatus("123")).thenReturn(EInvoiceSubmissionOutcome.pending("123", "hala isleniyor"));

        useCase.execute("123");

        assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.PROCESSING);
        verifyNoInteractions(invoiceEInvoiceUpdatePort);
        verify(eInvoiceSubmissionRepository, never()).save(any());
    }

    @Test
    void should_doNothing_when_providerReferenceUnknown() {
        when(eInvoiceSubmissionRepository.findByProviderReference("unknown")).thenReturn(Optional.empty());

        useCase.execute("unknown");

        verifyNoInteractions(eInvoiceGatewayPort, invoiceEInvoiceUpdatePort);
        verify(eInvoiceSubmissionRepository, never()).save(any());
    }

    @Test
    void should_doNothing_when_alreadySubmitted_idempotentRetry() {
        EInvoiceSubmission submission = aProcessingSubmission(UUID.randomUUID(), "123");
        submission.markSubmitted("ETTN-XYZ-789");
        when(eInvoiceSubmissionRepository.findByProviderReference("123")).thenReturn(Optional.of(submission));

        useCase.execute("123");

        verifyNoInteractions(eInvoiceGatewayPort, invoiceEInvoiceUpdatePort);
        verify(eInvoiceSubmissionRepository, never()).save(any());
    }
}
