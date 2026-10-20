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
class AcceptStaffInviteUseCaseTest {

    @Mock private StaffInviteRepository staffInviteRepository;
    @Mock private StaffUserRepository staffUserRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditPort auditPort;

    private AcceptStaffInviteUseCase useCase() {
        return new AcceptStaffInviteUseCase(staffInviteRepository, staffUserRepository, passwordEncoder, auditPort);
    }

    @Test
    void should_recordAuditEvent_withInviteTenantAndNewStaffAsActor() {
        UUID tenantId = UUID.randomUUID();
        StaffInvite invite = StaffInvite.create(tenantId, UUID.randomUUID(), "ayse@klinik.com", "Ayşe Yılmaz", StaffRole.VET, UUID.randomUUID());
        UUID newStaffId = UUID.randomUUID();
        when(staffInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));
        when(staffUserRepository.existsByEmail("ayse@klinik.com")).thenReturn(false);
        when(passwordEncoder.encode("sifre123")).thenReturn("hash");
        when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(inv -> {
            StaffUser saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", newStaffId);
            return saved;
        });

        useCase().execute(new AcceptStaffInviteCommand(invite.getToken(), "sifre123"));

        ArgumentCaptor<AuditRecord> recCaptor = ArgumentCaptor.forClass(AuditRecord.class);
        ArgumentCaptor<AuditActor> actorCaptor = ArgumentCaptor.forClass(AuditActor.class);
        verify(auditPort).record(recCaptor.capture(), actorCaptor.capture());
        AuditRecord rec = recCaptor.getValue();
        assertThat(rec.action()).isEqualTo(AuditAction.STAFF_INVITE_ACCEPTED);
        assertThat(rec.tenantId()).isEqualTo(tenantId);
        assertThat(rec.targetType()).isEqualTo("STAFF");
        assertThat(rec.targetId()).isEqualTo(newStaffId);
        assertThat(rec.targetLabel()).isEqualTo("Ayşe Yılmaz");
        assertThat(rec.summary()).isNull();
        AuditActor actor = actorCaptor.getValue();
        assertThat(actor.id()).isEqualTo(newStaffId);
        assertThat(actor.name()).isEqualTo("Ayşe Yılmaz");
        assertThat(actor.role()).isEqualTo("VET");
    }

    @Test
    void should_notRecord_whenInviteNotFound() {
        when(staffInviteRepository.findByToken("yok")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(new AcceptStaffInviteCommand("yok", "sifre123")))
            .isInstanceOf(StaffInviteNotFoundException.class);

        verifyNoInteractions(auditPort);
    }
}
