package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.ExtensionSecrets;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.integration.tarbil.domain.exception.InvalidPairingCodeUnauthorizedException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditActor;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PairExtensionUseCaseTest {

    @Mock private TarbilExtensionTokenRepository repository;
    @Mock private AuditPort auditPort;

    @Test
    void should_returnRawTokenAndStoreOnlyHash_when_codeValid() {
        TarbilExtensionToken pending = TarbilExtensionToken.issuePairing(
            UUID.randomUUID(), UUID.randomUUID(), ExtensionSecrets.sha256Hex("K7QM-2XPA"),
            Instant.now().plus(Duration.ofMinutes(10)), Instant.now());
        when(repository.findByPairingCodeHash(ExtensionSecrets.sha256Hex("K7QM-2XPA"))).thenReturn(Optional.of(pending));

        String raw = new PairExtensionUseCase(repository, auditPort).execute("k7qm 2xpa", "Muayene 1");

        assertThat(raw).startsWith("vtx_");
        assertThat(pending.getTokenHash()).isEqualTo(ExtensionSecrets.sha256Hex(raw));
        verify(repository).save(pending);
    }

    @Test
    void should_recordAuditEventWithCodeOwnerAsActor_when_codeValid() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        TarbilExtensionToken pending = TarbilExtensionToken.issuePairing(
            tenantId, staffId, ExtensionSecrets.sha256Hex("K7QM-2XPA"),
            Instant.now().plus(Duration.ofMinutes(10)), Instant.now());
        ReflectionTestUtils.setField(pending, "id", tokenId);
        when(repository.findByPairingCodeHash(ExtensionSecrets.sha256Hex("K7QM-2XPA"))).thenReturn(Optional.of(pending));

        new PairExtensionUseCase(repository, auditPort).execute("k7qm 2xpa", "Muayene 1");

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        ArgumentCaptor<AuditActor> actorCaptor = ArgumentCaptor.forClass(AuditActor.class);
        verify(auditPort).record(captor.capture(), actorCaptor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.tenantId()).isEqualTo(tenantId);
        assertThat(rec.action()).isEqualTo(AuditAction.TARBIL_EXTENSION_PAIRED);
        assertThat(rec.targetType()).isEqualTo("EXTENSION_TOKEN");
        assertThat(rec.targetId()).isEqualTo(tokenId);
        assertThat(rec.targetLabel()).isEqualTo("Muayene 1");
        assertThat(rec.summary()).isNull();
        AuditActor actor = actorCaptor.getValue();
        assertThat(actor.type()).isEqualTo(AuditActor.ActorType.STAFF);
        assertThat(actor.id()).isEqualTo(staffId);
        assertThat(actor.name()).isNull();
    }

    @Test
    void should_throwUnauthorized_when_codeUnknown() {
        when(repository.findByPairingCodeHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new PairExtensionUseCase(repository, auditPort).execute("AAAA-BBBB", "x"))
            .isInstanceOf(InvalidPairingCodeUnauthorizedException.class);
        verifyNoInteractions(auditPort);
    }
}
