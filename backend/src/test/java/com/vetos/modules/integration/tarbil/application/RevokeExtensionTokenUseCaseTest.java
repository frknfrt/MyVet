package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilExtensionTokenNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevokeExtensionTokenUseCaseTest {

    @Mock private TarbilExtensionTokenRepository repository;
    @Mock private AuditPort auditPort;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID ownerStaffId = UUID.randomUUID();

    private TarbilExtensionToken token() {
        TarbilExtensionToken t = TarbilExtensionToken.issuePairing(
            tenantId, ownerStaffId, "c", Instant.now().plus(Duration.ofMinutes(10)), Instant.now());
        t.pair("h", "x", Instant.now());
        return t;
    }

    @Test
    void should_revoke_when_callerOwnsToken() {
        UUID id = UUID.randomUUID();
        TarbilExtensionToken t = token();
        when(repository.findById(id)).thenReturn(Optional.of(t));

        new RevokeExtensionTokenUseCase(repository, auditPort).execute(tenantId, ownerStaffId, false, id);

        assertThat(t.isUsable()).isFalse();
    }

    @Test
    void should_recordAuditEvent_onSuccess() {
        UUID id = UUID.randomUUID();
        TarbilExtensionToken t = token();
        ReflectionTestUtils.setField(t, "id", id);
        when(repository.findById(id)).thenReturn(Optional.of(t));

        new RevokeExtensionTokenUseCase(repository, auditPort).execute(tenantId, ownerStaffId, false, id);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.tenantId()).isEqualTo(tenantId);
        assertThat(rec.action()).isEqualTo(AuditAction.TARBIL_EXTENSION_REVOKED);
        assertThat(rec.targetType()).isEqualTo("EXTENSION_TOKEN");
        assertThat(rec.targetId()).isEqualTo(id);
        assertThat(rec.targetLabel()).isEqualTo("x");
        assertThat(rec.summary()).isNull();
    }

    @Test
    void should_throwNotFound_when_nonAdminRevokesOthersToken() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(token()));

        assertThatThrownBy(() -> new RevokeExtensionTokenUseCase(repository, auditPort).execute(tenantId, UUID.randomUUID(), false, id))
            .isInstanceOf(TarbilExtensionTokenNotFoundException.class);
        verifyNoInteractions(auditPort);
    }

    @Test
    void should_revoke_when_adminRevokesOthersToken() {
        UUID id = UUID.randomUUID();
        TarbilExtensionToken t = token();
        when(repository.findById(id)).thenReturn(Optional.of(t));

        new RevokeExtensionTokenUseCase(repository, auditPort).execute(tenantId, UUID.randomUUID(), true, id);

        assertThat(t.isUsable()).isFalse();
    }

    @Test
    void should_throwNotFound_when_tokenInAnotherTenant() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(token()));

        assertThatThrownBy(() -> new RevokeExtensionTokenUseCase(repository, auditPort).execute(UUID.randomUUID(), ownerStaffId, true, id))
            .isInstanceOf(TarbilExtensionTokenNotFoundException.class);
    }
}
