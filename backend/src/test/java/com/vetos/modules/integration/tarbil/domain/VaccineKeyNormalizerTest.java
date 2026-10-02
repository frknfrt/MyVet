package com.vetos.modules.integration.tarbil.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VaccineKeyNormalizerTest {

    @Test
    void should_normalizeTurkishCaseAndWhitespace() {
        assertThat(VaccineKeyNormalizer.normalize("  KUDUZ   AŞISI ")).isEqualTo("kuduz aşısı");
        assertThat(VaccineKeyNormalizer.normalize("Kuduz Aşısı")).isEqualTo("kuduz aşısı");
        assertThat(VaccineKeyNormalizer.normalize("İÇ PARAZİT")).isEqualTo("iç parazit");
        assertThat(VaccineKeyNormalizer.normalize("ISIRGAN")).isEqualTo("ısırgan");
    }

    @Test
    void should_returnEmpty_when_nullOrBlank() {
        assertThat(VaccineKeyNormalizer.normalize(null)).isEmpty();
        assertThat(VaccineKeyNormalizer.normalize("   ")).isEmpty();
    }
}
