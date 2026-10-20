package com.vetos.modules.integration.efatura.infrastructure.metrics;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** E-fatura kuyrugu: PROCESSING (GIB resmilesmesi bekleyen) da "bekleyen" sayilir. */
@Component
class EInvoiceQueueMetrics {

    EInvoiceQueueMetrics(MeterRegistry registry, EInvoiceSubmissionRepository repository) {
        Gauge.builder("vetly.queue.pending", repository,
                r -> r.countByStatus(EInvoiceSubmissionStatus.PENDING) + r.countByStatus(EInvoiceSubmissionStatus.PROCESSING))
            .tag("queue", "efatura")
            .register(registry);
        Gauge.builder("vetly.queue.failed", repository, r -> r.countByStatus(EInvoiceSubmissionStatus.FAILED))
            .tag("queue", "efatura")
            .register(registry);
    }
}
