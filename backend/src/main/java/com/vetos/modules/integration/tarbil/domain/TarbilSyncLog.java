package com.vetos.modules.integration.tarbil.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tarbil_sync_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarbilSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_type", nullable = false)
    private TarbilSyncType syncType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TarbilSyncStatus status;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    public static TarbilSyncLog queue(UUID patientId, TarbilSyncType syncType, String payload) {
        TarbilSyncLog log = new TarbilSyncLog();
        log.patientId = patientId;
        log.syncType = syncType;
        log.payload = payload;
        log.status = TarbilSyncStatus.PENDING;
        log.attemptedAt = Instant.now();
        return log;
    }

    public void markSynced() {
        this.status = TarbilSyncStatus.SYNCED;
        this.attemptedAt = Instant.now();
    }

    public void markFailed() {
        this.status = TarbilSyncStatus.FAILED;
        this.attemptedAt = Instant.now();
    }

    public void markRetrying() {
        this.status = TarbilSyncStatus.PENDING;
        this.attemptedAt = Instant.now();
    }
}
