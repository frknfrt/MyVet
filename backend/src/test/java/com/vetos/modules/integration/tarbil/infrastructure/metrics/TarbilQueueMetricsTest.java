package com.vetos.modules.integration.tarbil.infrastructure.metrics;

import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TarbilQueueMetricsTest {

    @Test
    void should_reportPendingSubmissions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TarbilSubmissionRepository repository = mock(TarbilSubmissionRepository.class);
        when(repository.countByStatus(TarbilSyncStatus.PENDING)).thenReturn(7L);
        new TarbilQueueMetrics(registry, repository);

        assertThat(registry.get("vetly.queue.pending").tag("queue", "tarbil").gauge().value()).isEqualTo(7.0);
        assertThat(registry.find("vetly.queue.failed").tag("queue", "tarbil").gauge()).isNull();
    }
}
