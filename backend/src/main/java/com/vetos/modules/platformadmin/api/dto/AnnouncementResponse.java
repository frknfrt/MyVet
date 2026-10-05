package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.Announcement;

import java.time.Instant;
import java.util.UUID;

public record AnnouncementResponse(
    UUID id,
    String title,
    String body,
    String createdByAdminEmail,
    int recipientCount,
    Instant createdAt
) {
    public static AnnouncementResponse from(Announcement a) {
        return new AnnouncementResponse(a.getId(), a.getTitle(), a.getBody(), a.getCreatedByAdminEmail(), a.getRecipientCount(), a.getCreatedAt());
    }
}
