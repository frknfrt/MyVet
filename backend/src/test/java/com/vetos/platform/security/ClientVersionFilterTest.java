package com.vetos.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ClientVersionFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private MockHttpServletResponse run(String min, String header) throws Exception {
        ClientVersionFilter filter = new ClientVersionFilter(ClientVersion.parse(min), objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tarbil-extension/me");
        if (header != null) {
            request.addHeader(ClientVersionFilter.HEADER, header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        if (response.getStatus() == 200) {
            assertThat(chain.getRequest()).as("istek zincire gecmeli").isNotNull();
        } else {
            assertThat(chain.getRequest()).as("istek zincire gecmemeli").isNull();
        }
        return response;
    }

    @Test
    void should_reject_when_versionOlderThanMin() throws Exception {
        MockHttpServletResponse response = run("0.2.0", "0.1.9");

        assertThat(response.getStatus()).isEqualTo(426);
        assertThat(response.getHeader(ClientVersionFilter.MIN_HEADER)).isEqualTo("0.2.0");
        assertThat(response.getContentType()).startsWith("application/json");
        String body = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("\"errorCode\":\"CLIENT_UPGRADE_REQUIRED\"");
        assertThat(body).contains("Ayarlar > Entegrasyonlar");
        assertThat(body).contains("\"path\":\"/api/v1/tarbil-extension/me\"");
    }

    @Test
    void should_pass_when_versionEqualOrNewer() throws Exception {
        assertThat(run("0.2.0", "0.2.0").getStatus()).isEqualTo(200);
        assertThat(run("0.2.0", "0.10.0").getStatus()).isEqualTo(200);
        assertThat(run("0.2.0", " 0.2.0 ").getStatus()).isEqualTo(200);
    }

    @Test
    void should_treatMissingHeaderAs010() throws Exception {
        assertThat(run("0.1.0", null).getStatus()).isEqualTo(200);
        assertThat(run("0.2.0", null).getStatus()).isEqualTo(426);
        assertThat(run("0.2.0", "  ").getStatus()).isEqualTo(426);
    }

    @Test
    void should_treatMalformedAsOldest() throws Exception {
        assertThat(run("0.1.0", "abc").getStatus()).isEqualTo(426);
        assertThat(run("0.0.0", "abc").getStatus()).isEqualTo(200);
    }
}
