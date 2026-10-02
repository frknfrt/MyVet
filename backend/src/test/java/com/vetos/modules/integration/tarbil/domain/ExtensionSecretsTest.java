package com.vetos.modules.integration.tarbil.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionSecretsTest {

    @Test
    void should_generateFormattedPairingCode_withoutAmbiguousCharacters() {
        for (int i = 0; i < 200; i++) {
            assertThat(ExtensionSecrets.newPairingCode())
                .matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{4}-[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{4}");
        }
    }

    @Test
    void should_generateUniqueTokensWithPrefix() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String token = ExtensionSecrets.newToken();
            assertThat(token).startsWith("vtx_").hasSize(47);
            tokens.add(token);
        }
        assertThat(tokens).hasSize(100);
    }

    @Test
    void should_normalizeUserTypedCode() {
        assertThat(ExtensionSecrets.normalizePairingCode(" k7qm 2xpa ")).isEqualTo("K7QM-2XPA");
        assertThat(ExtensionSecrets.normalizePairingCode("k7qm-2xpa")).isEqualTo("K7QM-2XPA");
    }

    @Test
    void should_hashDeterministically() {
        assertThat(ExtensionSecrets.sha256Hex("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
