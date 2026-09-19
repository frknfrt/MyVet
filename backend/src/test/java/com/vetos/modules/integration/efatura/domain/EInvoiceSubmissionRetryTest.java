package com.vetos.modules.integration.efatura.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EInvoiceSubmissionRetryTest {

    private EInvoiceSubmission aSubmission() {
        return EInvoiceSubmission.queue(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EInvoiceDocumentType.E_ARSIV,
            new BigDecimal("120.00"), new BigDecimal("20.00")
        );
    }

    @Test
    void should_incrementAttemptCount_and_setNextRetryAt_when_markFailed() {
        EInvoiceSubmission submission = aSubmission();
        Instant nextRetry = Instant.now().plusSeconds(120);

        submission.markFailed("saglayici hatasi", nextRetry);

        assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.FAILED);
        assertThat(submission.getFailureReason()).isEqualTo("saglayici hatasi");
        assertThat(submission.getAttemptCount()).isEqualTo(1);
        assertThat(submission.getNextRetryAt()).isEqualTo(nextRetry);
    }

    @Test
    void should_clearNextRetryAt_when_markRetrying() {
        EInvoiceSubmission submission = aSubmission();
        submission.markFailed("hata", Instant.now().plusSeconds(120));

        submission.markRetrying();

        assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.PENDING);
        assertThat(submission.getNextRetryAt()).isNull();
    }
}
