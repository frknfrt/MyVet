package com.vetos.modules.integration.tarbil.domain;

import com.vetos.modules.integration.tarbil.domain.exception.InvalidPairingCodeUnauthorizedException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarbilExtensionTokenTest {

    private final Instant now = Instant.parse("2026-10-02T10:00:00Z");

    private TarbilExtensionToken pending() {
        return TarbilExtensionToken.issuePairing(UUID.randomUUID(), UUID.randomUUID(), "codehash", now.plus(Duration.ofMinutes(10)), now);
    }

    @Test
    void should_becomeUsableAndClearCode_when_pairedBeforeExpiry() {
        TarbilExtensionToken token = pending();

        token.pair("tokenhash", "Muayene 1", now.plus(Duration.ofMinutes(5)));

        assertThat(token.isUsable()).isTrue();
        assertThat(token.getTokenHash()).isEqualTo("tokenhash");
        assertThat(token.getPairingCodeHash()).isNull();
        assertThat(token.getLabel()).isEqualTo("Muayene 1");
    }

    @Test
    void should_reject_when_codeExpired() {
        TarbilExtensionToken token = pending();

        assertThatThrownBy(() -> token.pair("tokenhash", "x", now.plus(Duration.ofMinutes(11))))
            .isInstanceOf(InvalidPairingCodeUnauthorizedException.class);
        assertThat(token.isUsable()).isFalse();
    }

    @Test
    void should_reject_when_pairedTwice() {
        TarbilExtensionToken token = pending();
        token.pair("tokenhash", "x", now);

        assertThatThrownBy(() -> token.pair("other", "y", now))
            .isInstanceOf(InvalidPairingCodeUnauthorizedException.class);
    }

    @Test
    void should_notBeUsable_when_revoked() {
        TarbilExtensionToken token = pending();
        token.pair("tokenhash", "x", now);

        token.revoke(now);

        assertThat(token.isUsable()).isFalse();
    }

    @Test
    void should_updateLastUsedOnlyAfterFiveMinutes() {
        TarbilExtensionToken token = pending();
        token.pair("tokenhash", "x", now);

        token.touch(now.plusSeconds(60));
        assertThat(token.getLastUsedAt()).isEqualTo(now.plusSeconds(60));
        token.touch(now.plusSeconds(120));
        assertThat(token.getLastUsedAt()).isEqualTo(now.plusSeconds(60));
        token.touch(now.plusSeconds(60 + 301));
        assertThat(token.getLastUsedAt()).isEqualTo(now.plusSeconds(361));
    }
}
