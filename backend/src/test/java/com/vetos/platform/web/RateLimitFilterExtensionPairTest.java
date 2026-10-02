package com.vetos.platform.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterExtensionPairTest {

    private MockHttpServletResponse call(RateLimitFilter filter, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        return response;
    }

    @Test
    void should_return429_when_pairingCodeGuessedMoreThanTenTimes() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();
        for (int i = 0; i < 10; i++) {
            assertThat(call(filter, "/api/v1/tarbil-extension/pair").getStatus()).isEqualTo(200);
        }

        assertThat(call(filter, "/api/v1/tarbil-extension/pair").getStatus()).isEqualTo(429);
    }

    @Test
    void should_notLimit_when_otherExtensionEndpoint() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();
        for (int i = 0; i < 20; i++) {
            assertThat(call(filter, "/api/v1/tarbil-extension/pending").getStatus()).isEqualTo(200);
        }
    }
}
