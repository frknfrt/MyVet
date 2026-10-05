package com.vetos.modules.platformadmin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform admin'in tum (askiya alinmamis) kiracilarin faturalama
 * iletisim adresine gonderdigi bir duyurunun kalici kaydi -- "kime ne
 * zaman ne soyledik" takibi icin. Gercek gonderim PlatformAnnouncementEmailPort
 * uzerinden yapilir (bu ortamda mock/log -- bkz. PlatformBillingEmailPort ile
 * ayni kisit).
 */
@Entity
@Table(name = "announcements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_by_admin_id", nullable = false)
    private UUID createdByAdminId;

    @Column(name = "created_by_admin_email", nullable = false)
    private String createdByAdminEmail;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Announcement record(String title, String body, UUID createdByAdminId, String createdByAdminEmail, int recipientCount) {
        Announcement announcement = new Announcement();
        announcement.title = title;
        announcement.body = body;
        announcement.createdByAdminId = createdByAdminId;
        announcement.createdByAdminEmail = createdByAdminEmail;
        announcement.recipientCount = recipientCount;
        announcement.createdAt = Instant.now();
        return announcement;
    }
}
