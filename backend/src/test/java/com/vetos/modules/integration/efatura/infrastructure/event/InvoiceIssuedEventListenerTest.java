package com.vetos.modules.integration.efatura.infrastructure.event;

import com.vetos.modules.billing.domain.event.InvoiceIssuedEvent;
import com.vetos.modules.integration.efatura.application.QueueEInvoiceSubmissionUseCase;
import com.vetos.platform.feature.Feature;
import com.vetos.platform.feature.FeatureAccessPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceIssuedEventListenerTest {

    @Mock private QueueEInvoiceSubmissionUseCase queue;
    @Mock private FeatureAccessPort featureAccess;

    private InvoiceIssuedEvent event(UUID tenantId) {
        return new InvoiceIssuedEvent(UUID.randomUUID(), tenantId, UUID.randomUUID(),
            new BigDecimal("120.00"), new BigDecimal("20.00"), Instant.now());
    }

    @Test
    void should_notQueue_when_efaturaDisabled() {
        UUID tenantId = UUID.randomUUID();
        when(featureAccess.isEnabled(tenantId, Feature.E_FATURA)).thenReturn(false);

        new InvoiceIssuedEventListener(queue, featureAccess).onInvoiceIssued(event(tenantId));

        verifyNoInteractions(queue);
    }

    @Test
    void should_queue_when_efaturaEnabled() {
        UUID tenantId = UUID.randomUUID();
        when(featureAccess.isEnabled(tenantId, Feature.E_FATURA)).thenReturn(true);

        new InvoiceIssuedEventListener(queue, featureAccess).onInvoiceIssued(event(tenantId));

        verify(queue).execute(eq(tenantId), any(), any(), any(), any(), any());
    }
}
