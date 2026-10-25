package com.vetos.modules.ai.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalDataRedactorTest {

    @ParameterizedTest
    @ValueSource(strings = {"0555 123 45 67", "05551234567", "+90 555 123 45 67", "555-123-45-67", "(0555) 123 45 67",
        "0212 345 67 89", "905551234567"})
    void hidesPhoneNumbers(String phone) {
        assertThat(PersonalDataRedactor.redact("Sahibi arasın: " + phone + " akşam"))
            .isEqualTo("Sahibi arasın: [telefon gizlendi] akşam");
    }

    @Test
    void hidesTurkishIdNumbers() {
        assertThat(PersonalDataRedactor.redact("TC 12345678901 sahibi")).isEqualTo("TC [TC gizlendi] sahibi");
    }

    @Test
    void hidesEmailAddresses() {
        assertThat(PersonalDataRedactor.redact("rapor ali.veli+vet@ornek.com.tr adresine")).isEqualTo("rapor [e-posta gizlendi] adresine");
    }

    @Test
    void leavesClinicalNumbersAlone() {
        String clinical = "- 2026-03-01 Biyokimya\n  * BUN: 45 mg/dL (ref 7-27) YÜKSEK\n  * PLT: 250000 /µL\nMikroçip 900123456789012, Kilo: 31.5 kg, 1.5 mg günde 2";
        assertThat(PersonalDataRedactor.redact(clinical)).isEqualTo(clinical);
    }

    @Test
    void nullStaysNull() {
        assertThat(PersonalDataRedactor.redact(null)).isNull();
    }
}
