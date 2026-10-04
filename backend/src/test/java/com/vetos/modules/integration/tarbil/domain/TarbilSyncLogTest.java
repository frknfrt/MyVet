package com.vetos.modules.integration.tarbil.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TarbilSyncLogTest {

    private TarbilSyncLog aLog() {
        return TarbilSyncLog.queue(UUID.randomUUID(), UUID.randomUUID(), TarbilSyncType.VACCINATION, "{}");
    }

    @Test
    void should_storeTenantId_when_queued() {
        UUID tenantId = UUID.randomUUID();
        TarbilSyncLog log = TarbilSyncLog.queue(tenantId, UUID.randomUUID(), TarbilSyncType.VACCINATION, "{}");

        assertThat(log.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void should_incrementAttemptCount_and_setNextRetryAt_and_storeFailureReason_when_markFailed() {
        TarbilSyncLog log = aLog();
        Instant nextRetry = Instant.now().plusSeconds(120);

        log.markFailed("Bakanlik API zaman asimi", nextRetry);

        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.FAILED);
        assertThat(log.getAttemptCount()).isEqualTo(1);
        assertThat(log.getNextRetryAt()).isEqualTo(nextRetry);
        assertThat(log.getFailureReason()).isEqualTo("Bakanlik API zaman asimi");
    }

    @Test
    void should_clearNextRetryAt_and_clearFailureReason_when_markRetrying() {
        TarbilSyncLog log = aLog();
        log.markFailed("gecici hata", Instant.now().plusSeconds(120));

        log.markRetrying();

        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.PENDING);
        assertThat(log.getNextRetryAt()).isNull();
        assertThat(log.getFailureReason()).isNull();
    }

    @Test
    void should_clearFailureReason_when_markSynced() {
        TarbilSyncLog log = aLog();
        log.markFailed("gecici hata", Instant.now().plusSeconds(120));

        log.markSynced();

        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.SYNCED);
        assertThat(log.getFailureReason()).isNull();
    }
}
