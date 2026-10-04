package com.vetos.modules.integration.tarbil.domain;

import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionStateConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarbilSubmissionTest {

    private final UUID staffId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-02T10:00:00Z");

    private TarbilSubmission aLog() {
        return TarbilSubmission.queueVaccination(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void should_startPending_when_queued() {
        UUID vaccinationId = UUID.randomUUID();
        TarbilSubmission log = TarbilSubmission.queueVaccination(UUID.randomUUID(), UUID.randomUUID(), vaccinationId);

        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.PENDING);
        assertThat(log.getSourceId()).isEqualTo(vaccinationId);
        assertThat(log.getQueuedAt()).isNotNull();
    }

    @Test
    void should_recordSubmission_when_markSubmittedFromPending() {
        TarbilSubmission log = aLog();

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
        TarbilSubmission log = aLog();
        log.markSubmitted(staffId, TarbilConfirmationMethod.AUTO, "TRB-1", now);

        boolean changed = log.markSubmitted(UUID.randomUUID(), TarbilConfirmationMethod.MANUAL, null, now.plusSeconds(60));

        assertThat(changed).isFalse();
        assertThat(log.getConfirmationMethod()).isEqualTo(TarbilConfirmationMethod.AUTO);
        assertThat(log.getTarbilReference()).isEqualTo("TRB-1");
        assertThat(log.getSubmittedAt()).isEqualTo(now);
    }

    @Test
    void should_recordSubmission_when_markSubmittedWhileDismissed() {
        // TARBIL'e gercekten kaydedildiyse "bildirilmeyecek" niyeti olgunun onune gecemez.
        TarbilSubmission log = aLog();
        log.dismiss(staffId, "Bildirim gerekmiyor", now);

        assertThat(log.markSubmitted(staffId, TarbilConfirmationMethod.MANUAL, null, now)).isTrue();

        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.SUBMITTED);
        assertThat(log.getDismissedReason()).isNull();
    }

    @Test
    void should_dismissAndRestore_when_pending() {
        TarbilSubmission log = aLog();

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
        TarbilSubmission log = aLog();
        log.markSubmitted(staffId, TarbilConfirmationMethod.AUTO, null, now);

        assertThatThrownBy(() -> log.dismiss(staffId, "x", now))
            .isInstanceOf(TarbilSubmissionStateConflictException.class);
    }

    @Test
    void should_throwConflict_when_restoringPending() {
        assertThatThrownBy(() -> aLog().restore())
            .isInstanceOf(TarbilSubmissionStateConflictException.class);
    }

    @Test
    void should_carryDocumentTypeAndSource_when_queuedGenerically() {
        UUID tenantId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();

        TarbilSubmission submission = TarbilSubmission.queue(tenantId, TarbilDocumentType.PRESCRIPTION, null, prescriptionId);

        assertThat(submission.getDocumentType()).isEqualTo(TarbilDocumentType.PRESCRIPTION);
        assertThat(submission.getSourceId()).isEqualTo(prescriptionId);
        assertThat(submission.getPatientId()).isNull();
        assertThat(submission.getStatus()).isEqualTo(TarbilSyncStatus.PENDING);
    }

    @Test
    void should_beVaccination_when_queuedForVaccination() {
        TarbilSubmission submission = TarbilSubmission.queueVaccination(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThat(submission.getDocumentType()).isEqualTo(TarbilDocumentType.VACCINATION);
    }
}
