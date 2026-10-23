package com.vetos.modules.platformadmin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin panelinden gonderilen bir e-postanin kalici kaydi -- "kime, hangi
 * sablonla, ne zaman yazdik" takibi icin; Announcement ile ayni amac/desen
 * (bkz. o sinifin javadoc'u). Govde saklanmaz (gonderim aninda render
 * edilip atilir) -- sadece sablon tipi, alici ve konu.
 */
@Entity
@Table(name = "admin_sent_emails")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminSentEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_type", nullable = false)
    private String templateType;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(nullable = false, columnDefinition = "text")
    private String subject;

    @Column(name = "sent_by_admin_id", nullable = false)
    private UUID sentByAdminId;

    @Column(name = "sent_by_admin_email", nullable = false)
    private String sentByAdminEmail;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    public static AdminSentEmail record(EmailTemplateType type, String recipientEmail, String subject, UUID sentByAdminId, String sentByAdminEmail) {
        AdminSentEmail entry = new AdminSentEmail();
        entry.templateType = type.name();
        entry.recipientEmail = recipientEmail;
        entry.subject = subject;
        entry.sentByAdminId = sentByAdminId;
        entry.sentByAdminEmail = sentByAdminEmail;
        entry.sentAt = Instant.now();
        return entry;
    }
}
