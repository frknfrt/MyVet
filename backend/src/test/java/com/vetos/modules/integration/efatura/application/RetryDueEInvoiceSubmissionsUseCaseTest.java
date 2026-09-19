package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceDocumentType;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryDueEInvoiceSubmissionsUseCaseTest {

    @Mock private EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    @Mock private EInvoiceSubmissionExecutor eInvoiceSubmissionExecutor;

    private EInvoiceSubmission aFailedSubmission() {
        EInvoiceSubmission submission = EInvoiceSubmission.queue(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EInvoiceDocumentType.E_ARSIV,
            new BigDecimal("120.00"), new BigDecimal("20.00")
        );
        submission.markFailed("hata", Instant.now());
        return submission;
    }

    @Test
    void should_markClaimedSubmissionsRetrying_and_returnClaimedCount() {
        RetryDueEInvoiceSubmissionsUseCase useCase =
            new RetryDueEInvoiceSubmissionsUseCase(eInvoiceSubmissionRepository, eInvoiceSubmissionExecutor);
        EInvoiceSubmission submission = aFailedSubmission();
        when(eInvoiceSubmissionRepository.claimDueForRetry(any(), anyInt())).thenReturn(List.of(submission));

        int claimedCount = useCase.execute();

        assertThat(claimedCount).isEqualTo(1);
        assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.PENDING);
        verify(eInvoiceSubmissionRepository).save(submission);
    }

    /**
     * Bu testin ADI ve amacı bilinçli olarak vurgulu: repository sahte
     * (mock) olduğu için gerçek "PROCESSING asla claim edilmez" garantisi
     * burada değil, native SQL'in kendisinde (status = 'FAILED' filtresi,
     * bkz. Step 2) sağlanıyor. Bu test yalnızca use-case'in claim edilen
     * HER kaydı -- repository ne dönerse -- körü körüne retry'e soktuğunu,
     * yani use-case katmanında status'e göre ekstra bir filtre OLMADIĞINI
     * doğruluyor; gerçek koruma tamamen sorgunun sorumluluğunda.
     */
    @Test
    void should_returnZero_when_repositoryClaimsNothing() {
        RetryDueEInvoiceSubmissionsUseCase useCase =
            new RetryDueEInvoiceSubmissionsUseCase(eInvoiceSubmissionRepository, eInvoiceSubmissionExecutor);
        when(eInvoiceSubmissionRepository.claimDueForRetry(any(), anyInt())).thenReturn(List.of());

        int claimedCount = useCase.execute();

        assertThat(claimedCount).isZero();
        verify(eInvoiceSubmissionRepository, never()).save(any());
    }
}
