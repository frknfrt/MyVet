package com.vetos.modules.integration.tarbil.domain;

import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionStateConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarbilSyncLogTest {

    private final UUID staffId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-02T10:00:00Z");

    private TarbilSyncLog aLog() {
        return TarbilSyncLog.queueVaccination(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void should_startPending_when_queued() {
        UUID vaccinationId = UUID.randomUUID();
        TarbilSyncLog log = TarbilSyncLog.queueVaccination(UUID.randomUUID(), UUID.randomUUID(), vaccinationId);

        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.PENDING);
        assertThat(log.getVaccinationRecordId()).isEqualTo(vaccinationId);
        assertThat(log.getQueuedAt()).isNotNull();
    }

    @Test
    void should_recordSubmission_when_markSubmittedFromPending() {
        TarbilSyncLog log = aLog();

        boolean changed = log.markSubmitted(staffId, TarbilConfirmationMethod.AUTO, "TRB-1", now);

        assertThat(changed).isTrue();
        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.SUBMITTED);
        assertThat(log.getSubmittedByStaffId()).isEqualTo(staffId);
        assertThat(log.getConfirmationMethod()).isEqualTo(TarbilConfirmationMethod.AUTO);
        assertThat(log.getTarbilReference()).isEqualTo("TRB-1");
        assertThat(log.getSubmittedAt()).isEqualTo(now);
    }

    @Test
    void should_keepFirstSubmission_when_markSubmittedTwice() {
        TarbilSyncLog log = aLog();
        log.markSubmitted(staffId, TarbilConfirmationMethod.AUTO, "TRB-1", now);

        boolean changed = log.markSubmitted(UUID.randomUUID(), TarbilConfirmationMethod.MANUAL, null, now.plusSeconds(60));

        assertThat(changed).isFalse();
        assertThat(log.getConfirmationMethod()).isEqualTo(TarbilConfirmationMethod.AUTO);
        assertThat(log.getTarbilReference()).isEqualTo("TRB-1");
        assertThat(log.getSubmittedAt()).isEqualTo(now);
    }

    @Test
    void should_throwConflict_when_markSubmittedWhileDismissed() {
        TarbilSyncLog log = aLog();
        log.dismiss(staffId, "Bildirim gerekmiyor", now);

        assertThatThrownBy(() -> log.markSubmitted(staffId, TarbilConfirmationMethod.MANUAL, null, now))
            .isInstanceOf(TarbilSubmissionStateConflictException.class);
    }

    @Test
    void should_dismissAndRestore_when_pending() {
        TarbilSyncLog log = aLog();

        log.dismiss(staffId, "Karma asi", now);
        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.DISMISSED);
        assertThat(log.getDismissedReason()).isEqualTo("Karma asi");

        log.restore();
        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.PENDING);
        assertThat(log.getDismissedReason()).isNull();
        assertThat(log.getDismissedAt()).isNull();
    }

    @Test
    void should_throwConflict_when_dismissingSubmitted() {
        TarbilSyncLog log = aLog();
        log.markSubmitted(staffId, TarbilConfirmationMethod.AUTO, null, now);

        assertThatThrownBy(() -> log.dismiss(staffId, "x", now))
            .isInstanceOf(TarbilSubmissionStateConflictException.class);
    }

    @Test
    void should_throwConflict_when_restoringPending() {
        assertThatThrownBy(() -> aLog().restore())
            .isInstanceOf(TarbilSubmissionStateConflictException.class);
    }
}
