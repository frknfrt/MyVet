package com.vetos.modules.notification.infrastructure.metrics;

import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationQueueMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NotificationLogRepository repository = mock(NotificationLogRepository.class);

    @Test
    void should_reportPendingAndFailedCounts() {
        when(repository.countByStatus(NotificationStatus.PENDING)).thenReturn(4L);
        when(repository.countByStatus(NotificationStatus.FAILED)).thenReturn(2L);
        new NotificationQueueMetrics(registry, repository);

        assertThat(registry.get("vetly.queue.pending").tag("queue", "notification").gauge().value()).isEqualTo(4.0);
        assertThat(registry.get("vetly.queue.failed").tag("queue", "notification").gauge().value()).isEqualTo(2.0);
    }

    @Test
    void should_reportNaN_when_databaseFails() {
        when(repository.countByStatus(NotificationStatus.PENDING)).thenThrow(new IllegalStateException("db yok"));
        new NotificationQueueMetrics(registry, repository);

        assertThat(registry.get("vetly.queue.pending").tag("queue", "notification").gauge().value()).isNaN();
    }
}
