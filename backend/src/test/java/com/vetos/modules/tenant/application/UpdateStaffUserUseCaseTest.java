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
class UpdateStaffUserUseCaseTest {

    @Mock private StaffUserRepository staffUserRepository;
    @Mock private AuditPort auditPort;

    private UpdateStaffUserUseCase useCase() {
        return new UpdateStaffUserUseCase(staffUserRepository, auditPort);
    }

    private AuditRecord execute(StaffRole currentRole, StaffRole newRole) {
        StaffUser staffUser = StaffUser.register(UUID.randomUUID(), UUID.randomUUID(), "Ayşe Yılmaz", "ayse@klinik.com", "hash", currentRole);
        UUID staffId = UUID.randomUUID();
        ReflectionTestUtils.setField(staffUser, "id", staffId);
        when(staffUserRepository.findById(staffId)).thenReturn(Optional.of(staffUser));

        useCase().execute(new UpdateStaffUserCommand(staffId, UUID.randomUUID(), "Ayşe Yeni", null, newRole, null, null, null));

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.action()).isEqualTo(AuditAction.STAFF_UPDATED);
        assertThat(rec.targetType()).isEqualTo("STAFF");
        assertThat(rec.targetId()).isEqualTo(staffId);
        assertThat(rec.targetLabel()).isEqualTo("Ayşe Yeni");
        return rec;
    }

    @Test
    void should_describeRoleChange() {
        assertThat(execute(StaffRole.VET, StaffRole.ADMIN).summary()).isEqualTo("Rol: VET → ADMIN");
    }

    @Test
    void should_describeOtherChanges() {
        assertThat(execute(StaffRole.VET, StaffRole.VET).summary()).isEqualTo("Bilgiler güncellendi");
    }

    @Test
    void should_notRecord_whenStaffNotFound() {
        UUID id = UUID.randomUUID();
        when(staffUserRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(
            new UpdateStaffUserCommand(id, UUID.randomUUID(), "X", null, StaffRole.VET, null, null, null)
        )).isInstanceOf(StaffUserNotFoundException.class);

        verifyNoInteractions(auditPort);
    }
}
