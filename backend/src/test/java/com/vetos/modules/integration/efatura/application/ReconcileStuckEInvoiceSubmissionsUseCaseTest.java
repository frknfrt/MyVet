package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconcileStuckEInvoiceSubmissionsUseCaseTest {

    @Mock private EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    @Mock private ApplyEInvoiceCallbackUseCase applyEInvoiceCallbackUseCase;

    @Test
    void should_callApplyCallback_forEachStaleProviderReference() {
        ReconcileStuckEInvoiceSubmissionsUseCase useCase =
            new ReconcileStuckEInvoiceSubmissionsUseCase(eInvoiceSubmissionRepository, applyEInvoiceCallbackUseCase);
        when(eInvoiceSubmissionRepository.findProviderReferencesByStatusAndAttemptedAtBefore(eq(EInvoiceSubmissionStatus.PROCESSING), any(Instant.class)))
            .thenReturn(List.of("ref-1", "ref-2"));

        int resolved = useCase.execute();

        assertThat(resolved).isEqualTo(2);
        verify(applyEInvoiceCallbackUseCase).execute("ref-1");
        verify(applyEInvoiceCallbackUseCase).execute("ref-2");
    }

    @Test
    void should_returnZero_and_callNothing_when_noStaleSubmissions() {
        ReconcileStuckEInvoiceSubmissionsUseCase useCase =
            new ReconcileStuckEInvoiceSubmissionsUseCase(eInvoiceSubmissionRepository, applyEInvoiceCallbackUseCase);
        when(eInvoiceSubmissionRepository.findProviderReferencesByStatusAndAttemptedAtBefore(eq(EInvoiceSubmissionStatus.PROCESSING), any(Instant.class)))
            .thenReturn(List.of());

        int resolved = useCase.execute();

        assertThat(resolved).isZero();
        verifyNoInteractions(applyEInvoiceCallbackUseCase);
    }
}
