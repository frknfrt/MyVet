package com.vetos.modules.platformadmin.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogEntryTest {

    @Test
    void should_captureAllFields_when_recorded() {
        UUID adminId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        AuditLogEntry entry = AuditLogEntry.record(adminId, "admin@vetly.com.tr", AuditAction.TENANT_SUSPENDED, "TENANT", targetId, "detay");

        assertThat(entry.getPlatformAdminId()).isEqualTo(adminId);
        assertThat(entry.getPlatformAdminEmail()).isEqualTo("admin@vetly.com.tr");
        assertThat(entry.getAction()).isEqualTo(AuditAction.TENANT_SUSPENDED);
        assertThat(entry.getTargetType()).isEqualTo("TENANT");
        assertThat(entry.getTargetId()).isEqualTo(targetId);
        assertThat(entry.getDetails()).isEqualTo("detay");
        assertThat(entry.getCreatedAt()).isNotNull();
    }

    @Test
    void should_allowNullTargetIdAndDetails() {
        AuditLogEntry entry = AuditLogEntry.record(UUID.randomUUID(), "admin@vetly.com.tr", AuditAction.PLAN_DELETED, "PLAN", null, null);

        assertThat(entry.getTargetId()).isNull();
        assertThat(entry.getDetails()).isNull();
    }
}
