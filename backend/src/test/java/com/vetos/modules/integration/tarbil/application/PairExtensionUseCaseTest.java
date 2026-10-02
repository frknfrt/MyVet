package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.ExtensionSecrets;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.integration.tarbil.domain.exception.InvalidPairingCodeUnauthorizedException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PairExtensionUseCaseTest {

    @Mock private TarbilExtensionTokenRepository repository;

    @Test
    void should_returnRawTokenAndStoreOnlyHash_when_codeValid() {
        TarbilExtensionToken pending = TarbilExtensionToken.issuePairing(
            UUID.randomUUID(), UUID.randomUUID(), ExtensionSecrets.sha256Hex("K7QM-2XPA"),
            Instant.now().plus(Duration.ofMinutes(10)), Instant.now());
        when(repository.findByPairingCodeHash(ExtensionSecrets.sha256Hex("K7QM-2XPA"))).thenReturn(Optional.of(pending));

        String raw = new PairExtensionUseCase(repository).execute("k7qm 2xpa", "Muayene 1");

        assertThat(raw).startsWith("vtx_");
        assertThat(pending.getTokenHash()).isEqualTo(ExtensionSecrets.sha256Hex(raw));
        verify(repository).save(pending);
    }

    @Test
    void should_throwUnauthorized_when_codeUnknown() {
        when(repository.findByPairingCodeHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new PairExtensionUseCase(repository).execute("AAAA-BBBB", "x"))
            .isInstanceOf(InvalidPairingCodeUnauthorizedException.class);
    }
}
