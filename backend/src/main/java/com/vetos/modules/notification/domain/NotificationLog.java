package com.vetos.modules.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "recipient_label")
    private String recipientLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Column(name = "recipient_contact", nullable = false)
    private String recipientContact;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(name = "related_entity_id")
    private UUID relatedEntityId;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    public static NotificationLog queue(
        UUID tenantId, UUID ownerId, UUID patientId, NotificationChannel channel, NotificationType notificationType,
        String recipientContact, String message, UUID relatedEntityId, String recipientLabel
    ) {
        NotificationLog log = new NotificationLog();
        log.tenantId = tenantId;
        log.ownerId = ownerId;
        log.patientId = patientId;
        log.channel = channel;
        log.notificationType = notificationType;
        log.recipientContact = recipientContact;
        log.message = message;
        log.relatedEntityId = relatedEntityId;
        log.recipientLabel = recipientLabel;
        log.status = NotificationStatus.PENDING;
        log.attemptedAt = Instant.now();
        return log;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.attemptedAt = Instant.now();
    }

    public void markFailed(Instant nextRetryAt) {
        this.status = NotificationStatus.FAILED;
        this.attemptedAt = Instant.now();
        this.attemptCount++;
        this.nextRetryAt = nextRetryAt;
    }

    public void markRetrying() {
        this.status = NotificationStatus.PENDING;
        this.attemptedAt = Instant.now();
        this.nextRetryAt = null;
    }
}
