package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilExtensionTokenNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevokeExtensionTokenUseCaseTest {

    @Mock private TarbilExtensionTokenRepository repository;
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

        new RevokeExtensionTokenUseCase(repository).execute(tenantId, ownerStaffId, false, id);

        assertThat(t.isUsable()).isFalse();
    }

    @Test
    void should_throwNotFound_when_nonAdminRevokesOthersToken() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(token()));

        assertThatThrownBy(() -> new RevokeExtensionTokenUseCase(repository).execute(tenantId, UUID.randomUUID(), false, id))
            .isInstanceOf(TarbilExtensionTokenNotFoundException.class);
    }

    @Test
    void should_revoke_when_adminRevokesOthersToken() {
        UUID id = UUID.randomUUID();
        TarbilExtensionToken t = token();
        when(repository.findById(id)).thenReturn(Optional.of(t));

        new RevokeExtensionTokenUseCase(repository).execute(tenantId, UUID.randomUUID(), true, id);

        assertThat(t.isUsable()).isFalse();
    }

    @Test
    void should_throwNotFound_when_tokenInAnotherTenant() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(token()));

        assertThatThrownBy(() -> new RevokeExtensionTokenUseCase(repository).execute(UUID.randomUUID(), ownerStaffId, true, id))
            .isInstanceOf(TarbilExtensionTokenNotFoundException.class);
    }
}
