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
class DeactivateStaffUserUseCaseTest {

    @Mock private StaffUserRepository staffUserRepository;
    @Mock private AuditPort auditPort;

    private DeactivateStaffUserUseCase useCase() {
        return new DeactivateStaffUserUseCase(staffUserRepository, auditPort);
    }

    @Test
    void should_recordAuditEvent_onSuccess() {
        StaffUser staffUser = StaffUser.register(UUID.randomUUID(), UUID.randomUUID(), "Ayşe Yılmaz", "ayse@klinik.com", "hash", StaffRole.VET);
        UUID staffId = UUID.randomUUID();
        ReflectionTestUtils.setField(staffUser, "id", staffId);
        when(staffUserRepository.findById(staffId)).thenReturn(Optional.of(staffUser));

        useCase().execute(staffId, UUID.randomUUID());

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.action()).isEqualTo(AuditAction.STAFF_DEACTIVATED);
        assertThat(rec.targetType()).isEqualTo("STAFF");
        assertThat(rec.targetId()).isEqualTo(staffId);
        assertThat(rec.targetLabel()).isEqualTo("Ayşe Yılmaz");
        assertThat(rec.summary()).isNull();
    }

    @Test
    void should_notRecord_whenStaffNotFound() {
        UUID id = UUID.randomUUID();
        when(staffUserRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(id, UUID.randomUUID()))
            .isInstanceOf(StaffUserNotFoundException.class);

        verifyNoInteractions(auditPort);
    }

    @Test
    void should_notRecord_whenActingOnSelf() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> useCase().execute(id, id)).isInstanceOf(SelfAccountManagementForbiddenException.class);

        verifyNoInteractions(auditPort);
    }
}
