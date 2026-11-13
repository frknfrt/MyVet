package com.vetos.modules.platformadmin.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = {"0532 111 22 33", "+90 532 111 22 33", "5321112233", "90(532)111-22-33", " 0532-111-2233 "})
    void should_normalizeToSameE164_when_phoneWrittenDifferently(String raw) {
        assertThat(PhoneNumbers.toE164Tr(raw)).isEqualTo("+905321112233");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0212 111 22 33", "123", "", "05321112233444", "abc", "+1 532 111 22 33"})
    void should_reject_when_phoneIsNotTurkishMobile(String raw) {
        assertThatThrownBy(() -> PhoneNumbers.toE164Tr(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Test
    void should_reject_when_phoneIsNull() {
        assertThatThrownBy(() -> PhoneNumbers.toE164Tr(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
