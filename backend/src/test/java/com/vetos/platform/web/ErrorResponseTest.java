package com.vetos.platform.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void should_carryRequestIdFromMdc() {
        MDC.put("requestId", "req-42-abcdef");

        assertThat(ErrorResponse.of("X", "m", "/p").requestId()).isEqualTo("req-42-abcdef");
        assertThat(ErrorResponse.ofValidation("m", List.of(), "/p").requestId()).isEqualTo("req-42-abcdef");
    }

    @Test
    void should_haveNullRequestId_when_mdcEmpty() {
        assertThat(ErrorResponse.of("X", "m", "/p").requestId()).isNull();
    }
}
