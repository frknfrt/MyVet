package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.billing.domain.InvoiceEInvoiceUpdatePort;
import com.vetos.modules.billing.domain.InvoiceLineRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceGatewayPort;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EInvoiceSubmissionExecutorBackoffTest {

    @Mock private EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    @Mock private EInvoiceGatewayPort eInvoiceGatewayPort;
    @Mock private OwnerLookupPort ownerLookupPort;
    @Mock private InvoiceLineRepository invoiceLineRepository;
    @Mock private InvoiceEInvoiceUpdatePort invoiceEInvoiceUpdatePort;

    @Test
    void should_scheduleShortBackoff_when_firstFailure() {
        EInvoiceSubmissionExecutor executor = new EInvoiceSubmissionExecutor(
            eInvoiceSubmissionRepository, eInvoiceGatewayPort, ownerLookupPort, invoiceLineRepository, invoiceEInvoiceUpdatePort
        );
        Instant before = Instant.now();

        Instant nextRetry = executor.computeNextRetryAt(1);

        assertThat(nextRetry).isAfter(before.plusSeconds(60)).isBefore(before.plusSeconds(180));
    }

    @Test
    void should_returnNull_when_fifthFailure_automaticRetriesExhausted() {
        EInvoiceSubmissionExecutor executor = new EInvoiceSubmissionExecutor(
            eInvoiceSubmissionRepository, eInvoiceGatewayPort, ownerLookupPort, invoiceLineRepository, invoiceEInvoiceUpdatePort
        );

        Instant nextRetry = executor.computeNextRetryAt(5);

        assertThat(nextRetry).isNull();
    }
}
