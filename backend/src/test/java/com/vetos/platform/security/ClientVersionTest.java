package com.vetos.platform.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientVersionTest {

    @Test
    void should_parseThreeNumbers() {
        assertThat(ClientVersion.parse("0.2.0")).isEqualTo(new ClientVersion(0, 2, 0));
        assertThat(ClientVersion.parse(" 1.10.3 ")).isEqualTo(new ClientVersion(1, 10, 3));
    }

    @Test
    void should_compareNumerically_notAsText() {
        assertThat(ClientVersion.parse("0.10.0")).isGreaterThan(ClientVersion.parse("0.9.0"));
        assertThat(ClientVersion.parse("1.0.0")).isGreaterThan(ClientVersion.parse("0.99.99"));
        assertThat(ClientVersion.parse("0.2.0")).isEqualByComparingTo(ClientVersion.parse("0.2.0"));
        assertThat(ClientVersion.parse("0.1.9")).isLessThan(ClientVersion.parse("0.2.0"));
    }

    @Test
    void should_rejectMalformed() {
        for (String bad : new String[] {"", "abc", "1.2", "1.2.3.4", "-1.0.0", "1.x.0", "v1.0.0", "1..0"}) {
            assertThat(ClientVersion.tryParse(bad)).as(bad).isEmpty();
        }
        assertThat(ClientVersion.tryParse(null)).isEmpty();
        assertThatThrownBy(() -> ClientVersion.parse("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_exposeDefaults() {
        assertThat(ClientVersion.ZERO).isEqualTo(new ClientVersion(0, 0, 0));
        assertThat(ClientVersion.WHEN_MISSING).isEqualTo(new ClientVersion(0, 1, 0));
    }
}
