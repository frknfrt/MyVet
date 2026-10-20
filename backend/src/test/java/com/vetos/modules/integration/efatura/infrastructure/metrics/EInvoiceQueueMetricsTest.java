package com.vetos.modules.integration.efatura.infrastructure.metrics;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EInvoiceQueueMetricsTest {

    @Test
    void should_countPendingAndProcessingAsPending() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EInvoiceSubmissionRepository repository = mock(EInvoiceSubmissionRepository.class);
        when(repository.countByStatus(EInvoiceSubmissionStatus.PENDING)).thenReturn(3L);
        when(repository.countByStatus(EInvoiceSubmissionStatus.PROCESSING)).thenReturn(5L);
        when(repository.countByStatus(EInvoiceSubmissionStatus.FAILED)).thenReturn(1L);
        new EInvoiceQueueMetrics(registry, repository);

        assertThat(registry.get("vetly.queue.pending").tag("queue", "efatura").gauge().value()).isEqualTo(8.0);
        assertThat(registry.get("vetly.queue.failed").tag("queue", "efatura").gauge().value()).isEqualTo(1.0);
    }
}
