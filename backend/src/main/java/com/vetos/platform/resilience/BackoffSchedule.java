package com.vetos.platform.resilience;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox (e-fatura, bildirim) otomatik tekrar deneme takvimi -- iki executor'daki kopya
 * RETRY_BACKOFF tablolarinin tek kaynagi. attemptCountAfterThisFailure 1..n -> sonraki deneme
 * zamani; takvim bitince null (yalniz elle "Tekrar Dene").
 */
public final class BackoffSchedule {

    public static final BackoffSchedule OUTBOX = new BackoffSchedule(
        Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofHours(6)
    );

    private final List<Duration> steps;

    public BackoffSchedule(Duration... steps) {
        this.steps = List.of(steps);
    }

    public Instant nextRetryAt(int attemptCountAfterThisFailure, Instant now) {
        int index = attemptCountAfterThisFailure - 1;
        if (index < 0 || index >= steps.size()) {
            return null;
        }
        return now.plus(steps.get(index));
    }
}
