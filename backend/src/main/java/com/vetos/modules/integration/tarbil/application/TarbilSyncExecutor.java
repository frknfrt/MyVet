package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Gercek bir mesaj kuyrugu (RabbitMQ/Kafka) Faz 1'de kurulu degil; ayni
 * "asenkron, cagirani bloklamayan, tekrar denenebilir" davranisi Spring
 * @Async + TarbilSyncLog outbox kaydiyla sagliyoruz. Ileride gercek bir
 * kuyruk eklenirse sadece bu sinifin ici degisir, QueueTarbilSyncUseCase'i
 * cagiran kod (patient/encounter event listener'lari) etkilenmez.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class TarbilSyncExecutor {

    private static final Duration[] RETRY_BACKOFF = {
        Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofHours(6)
    };

    private final TarbilSyncLogRepository tarbilSyncLogRepository;
    private final TarbilSyncPort tarbilSyncPort;

    Instant computeNextRetryAt(int attemptCountAfterThisFailure) {
        int index = attemptCountAfterThisFailure - 1;
        if (index >= RETRY_BACKOFF.length) return null;
        return Instant.now().plus(RETRY_BACKOFF[index]);
    }

    @Async
    @Transactional
    public void attemptSync(UUID logId) {
        TarbilSyncLog syncLog = tarbilSyncLogRepository.findById(logId).orElse(null);
        if (syncLog == null) {
            return;
        }

        TarbilSyncOutcome outcome = tarbilSyncPort.sync(
            new TarbilSyncRequest(syncLog.getPatientId(), syncLog.getSyncType(), syncLog.getPayload())
        );

        if (outcome.success()) {
            syncLog.markSynced();
        } else {
            syncLog.markFailed(computeNextRetryAt(syncLog.getAttemptCount() + 1));
            log.warn("TARBIL senkronu basarisiz: logId={}, sebep={}", logId, outcome.message());
        }
        tarbilSyncLogRepository.save(syncLog);
    }
}
