package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.*;
import com.vetos.modules.tenant.domain.*;
import com.vetos.modules.tenant.domain.exception.*;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditActor;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import com.vetos.platform.observability.LogMasking;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevokeStaffInviteUseCaseTest {

    @Mock private StaffInviteRepository staffInviteRepository;
    @Mock private AuditPort auditPort;

    private RevokeStaffInviteUseCase useCase() {
        return new RevokeStaffInviteUseCase(staffInviteRepository, auditPort);
    }

    @Test
    void should_recordAuditEvent_onSuccess() {
        String email = "ayse@klinik.com";
        StaffInvite invite = StaffInvite.create(UUID.randomUUID(), UUID.randomUUID(), email, "Ayşe Yılmaz", StaffRole.VET, UUID.randomUUID());
        UUID inviteId = UUID.randomUUID();
        ReflectionTestUtils.setField(invite, "id", inviteId);
        when(staffInviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));

        useCase().execute(inviteId);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.action()).isEqualTo(AuditAction.STAFF_INVITE_REVOKED);
        assertThat(rec.targetType()).isEqualTo("STAFF_INVITE");
        assertThat(rec.targetId()).isEqualTo(inviteId);
        assertThat(rec.targetLabel()).isEqualTo(LogMasking.email(email)).doesNotContain("ayse");
        assertThat(rec.summary()).isNull();
    }

    @Test
    void should_notRecord_whenInviteNotFound() {
        UUID id = UUID.randomUUID();
        when(staffInviteRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(id)).isInstanceOf(StaffInviteNotFoundException.class);

        verifyNoInteractions(auditPort);
    }
}
