package com.vetos.platform.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCookiesTest {

    @Test
    void should_issueHttpOnlySecureStrictCookie_scopedToApi() {
        String cookie = new SessionCookies(480, true).create("abc.def.ghi");

        assertThat(cookie).startsWith("vetly_session=abc.def.ghi")
            .contains("Path=/api", "Max-Age=28800", "Secure", "HttpOnly", "SameSite=Strict");
    }

    @Test
    void should_clearCookie_withZeroMaxAge() {
        String cookie = new SessionCookies(480, true).clear();

        assertThat(cookie).startsWith("vetly_session=;").contains("Max-Age=0", "Path=/api", "HttpOnly");
    }

    @Test
    void should_scopeAdminCookie_toPlatformAdminApi() {
        SessionCookies cookies = new SessionCookies(480, true);

        assertThat(cookies.createAdmin("a.b.c")).startsWith("vetly_admin_session=a.b.c")
            .contains("Path=/api/v1/platform-admin", "HttpOnly", "Secure", "SameSite=Strict");
        assertThat(cookies.clearAdmin()).contains("Max-Age=0", "Path=/api/v1/platform-admin");
    }
}
