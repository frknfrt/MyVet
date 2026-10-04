package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.AuditLogEntry;
import com.vetos.modules.platformadmin.domain.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecordAuditLogUseCaseTest {

    @Mock private AuditLogRepository auditLogRepository;

    @Test
    void should_saveAuditLogEntry_withGivenFields() {
        UUID adminId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        RecordAuditLogUseCase useCase = new RecordAuditLogUseCase(auditLogRepository);

        useCase.execute(adminId, "admin@vetly.com.tr", AuditAction.COUPON_CREATED, "COUPON", targetId, "WELCOME10");

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPlatformAdminId()).isEqualTo(adminId);
        assertThat(captor.getValue().getPlatformAdminEmail()).isEqualTo("admin@vetly.com.tr");
        assertThat(captor.getValue().getAction()).isEqualTo(AuditAction.COUPON_CREATED);
        assertThat(captor.getValue().getTargetType()).isEqualTo("COUPON");
        assertThat(captor.getValue().getTargetId()).isEqualTo(targetId);
        assertThat(captor.getValue().getDetails()).isEqualTo("WELCOME10");
    }
}
