package com.vetos.modules.patient.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TcKimlikValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"10000000146", "56673392584"})
    void should_returnTrue_for_validChecksums(String candidate) {
        assertThat(TcKimlikValidator.isValid(candidate)).isTrue();
    }

    @Test
    void should_returnFalse_when_checksumDigitsWrong() {
        // 10000000146 gecerliyken son iki haneyi bozuyoruz
        assertThat(TcKimlikValidator.isValid("10000000147")).isFalse();
    }

    @Test
    void should_returnFalse_when_firstDigitIsZero() {
        assertThat(TcKimlikValidator.isValid("01234567890")).isFalse();
    }

    @Test
    void should_returnFalse_when_notElevenDigits() {
        assertThat(TcKimlikValidator.isValid("123456789")).isFalse();
    }

    @Test
    void should_returnFalse_when_containsNonDigits() {
        assertThat(TcKimlikValidator.isValid("1234567890a")).isFalse();
    }

    @Test
    void should_returnFalse_when_null() {
        assertThat(TcKimlikValidator.isValid(null)).isFalse();
    }
}
