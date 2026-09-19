package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TarbilSyncExecutorBackoffTest {

    @Mock private TarbilSyncLogRepository tarbilSyncLogRepository;
    @Mock private TarbilSyncPort tarbilSyncPort;

    @Test
    void should_scheduleShortBackoff_when_firstFailure() {
        TarbilSyncExecutor executor = new TarbilSyncExecutor(tarbilSyncLogRepository, tarbilSyncPort);
        Instant before = Instant.now();

        Instant nextRetry = executor.computeNextRetryAt(1);

        assertThat(nextRetry).isAfter(before.plusSeconds(60)).isBefore(before.plusSeconds(180));
    }

    @Test
    void should_returnNull_when_fifthFailure_automaticRetriesExhausted() {
        TarbilSyncExecutor executor = new TarbilSyncExecutor(tarbilSyncLogRepository, tarbilSyncPort);

        Instant nextRetry = executor.computeNextRetryAt(5);

        assertThat(nextRetry).isNull();
    }
}
