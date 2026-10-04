package com.vetos.modules.platformadmin.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnnouncementTest {

    @Test
    void should_captureAllFields_when_recorded() {
        UUID adminId = UUID.randomUUID();

        Announcement announcement = Announcement.record("Bakim Bildirimi", "Bu gece bakim var.", adminId, "admin@vetly.com.tr", 42);

        assertThat(announcement.getTitle()).isEqualTo("Bakim Bildirimi");
        assertThat(announcement.getBody()).isEqualTo("Bu gece bakim var.");
        assertThat(announcement.getCreatedByAdminId()).isEqualTo(adminId);
        assertThat(announcement.getCreatedByAdminEmail()).isEqualTo("admin@vetly.com.tr");
        assertThat(announcement.getRecipientCount()).isEqualTo(42);
        assertThat(announcement.getCreatedAt()).isNotNull();
    }
}
