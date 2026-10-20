package com.vetos.modules.integration.tarbil.infrastructure.metrics;

import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Hekim onayi bekleyen TARBIL gonderimleri. "failed" yok: eklenti hata bildirmiyor,
 * kayit ya SUBMITTED ya DISMISSED olur.
 */
@Component
class TarbilQueueMetrics {

    TarbilQueueMetrics(MeterRegistry registry, TarbilSubmissionRepository repository) {
        Gauge.builder("vetly.queue.pending", repository, r -> r.countByStatus(TarbilSyncStatus.PENDING))
            .tag("queue", "tarbil")
            .register(registry);
    }
}
