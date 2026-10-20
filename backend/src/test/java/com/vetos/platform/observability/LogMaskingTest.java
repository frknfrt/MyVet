package com.vetos.platform.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskingTest {

    @Test
    void should_maskPhone_keepingFirstThreeAndLastTwoDigits() {
        assertThat(LogMasking.phone("05321234567")).isEqualTo("053******67");
        assertThat(LogMasking.phone("+90 532 123 45 67")).isEqualTo("905*******67");
    }

    @Test
    void should_maskShortOrNullPhone() {
        assertThat(LogMasking.phone("1234")).isEqualTo("***");
        assertThat(LogMasking.phone("")).isEqualTo("***");
        assertThat(LogMasking.phone(null)).isNull();
    }

    @Test
    void should_maskEmail_keepingFirstLetterAndDomain() {
        assertThat(LogMasking.email("furkan@gmail.com")).isEqualTo("f*****@gmail.com");
        assertThat(LogMasking.email("a@b.com")).isEqualTo("a@b.com");
        assertThat(LogMasking.email("gecersiz")).isEqualTo("***");
        assertThat(LogMasking.email("@b.com")).isEqualTo("***");
        assertThat(LogMasking.email(null)).isNull();
    }

    @Test
    void should_maskElevenDigitNumbers_only() {
        assertThat(LogMasking.elevenDigits("TCKN 12345678901 ok")).isEqualTo("TCKN 123******01 ok");
        assertThat(LogMasking.elevenDigits("tel:05321234567")).isEqualTo("tel:053******67");
        assertThat(LogMasking.elevenDigits("on hane 1234567890")).isEqualTo("on hane 1234567890");
        assertThat(LogMasking.elevenDigits("oniki 123456789012")).isEqualTo("oniki 123456789012");
        assertThat(LogMasking.elevenDigits("fatura ABC2026000000001")).isEqualTo("fatura ABC2026000000001");
        assertThat(LogMasking.elevenDigits("id 3f2a9c4e-1b7d-4e5f-9a0b-123456789012"))
            .isEqualTo("id 3f2a9c4e-1b7d-4e5f-9a0b-123456789012");
        assertThat(LogMasking.elevenDigits(null)).isNull();
    }

    @Test
    void should_truncateAndMaskBody() {
        String longBody = "{\"tckn\":\"12345678901\",\"x\":\"" + "a".repeat(400) + "\"}";

        String masked = LogMasking.body(longBody);

        assertThat(masked).startsWith("{\"tckn\":\"123******01\"");
        assertThat(masked).endsWith("…(+" + (longBody.length() - 300) + " karakter)");
        assertThat(masked).doesNotContain("12345678901");
        assertThat(LogMasking.body("kisa")).isEqualTo("kisa");
        assertThat(LogMasking.body(null)).isNull();
    }
}
