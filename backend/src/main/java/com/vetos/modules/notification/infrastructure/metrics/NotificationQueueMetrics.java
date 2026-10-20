package com.vetos.modules.notification.infrastructure.metrics;

import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Bildirim kuyrugu derinligi (spec 2026-10-14 S4.3). Deger scrape aninda DB'den sayilir;
 * sorgu hata verirse Micrometer gauge'u NaN raporlar, scrape kirilmaz.
 */
@Component
class NotificationQueueMetrics {

    NotificationQueueMetrics(MeterRegistry registry, NotificationLogRepository repository) {
        Gauge.builder("vetly.queue.pending", repository, r -> r.countByStatus(NotificationStatus.PENDING))
            .tag("queue", "notification")
            .register(registry);
        Gauge.builder("vetly.queue.failed", repository, r -> r.countByStatus(NotificationStatus.FAILED))
            .tag("queue", "notification")
            .register(registry);
    }
}
