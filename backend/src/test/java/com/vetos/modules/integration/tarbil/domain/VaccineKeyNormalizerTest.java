package com.vetos.modules.integration.tarbil.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VaccineKeyNormalizerTest {

    @Test
    void should_normalizeTurkishCaseAndWhitespace() {
        assertThat(VaccineKeyNormalizer.normalize("  KUDUZ   AŞISI ")).isEqualTo("kuduz aşisi");
        assertThat(VaccineKeyNormalizer.normalize("Kuduz Aşısı")).isEqualTo("kuduz aşisi");
        assertThat(VaccineKeyNormalizer.normalize("İÇ PARAZİT")).isEqualTo("iç parazit");
        assertThat(VaccineKeyNormalizer.normalize("ISIRGAN")).isEqualTo(VaccineKeyNormalizer.normalize("ısırgan"));
    }

    @Test
    void should_collapseLatinBrandNames_when_caseDiffers() {
        // I -> ı (tr) Latin marka adlarini boler: "NOBIVAC" ile "Nobivac" ayni anahtar olmali.
        assertThat(VaccineKeyNormalizer.normalize("NOBIVAC RABIES")).isEqualTo(VaccineKeyNormalizer.normalize("Nobivac Rabies"));
        assertThat(VaccineKeyNormalizer.normalize("Rabisin")).isEqualTo(VaccineKeyNormalizer.normalize("RABİSİN"));
    }

    @Test
    void should_returnEmpty_when_nullOrBlank() {
        assertThat(VaccineKeyNormalizer.normalize(null)).isEmpty();
        assertThat(VaccineKeyNormalizer.normalize("   ")).isEmpty();
    }
}
