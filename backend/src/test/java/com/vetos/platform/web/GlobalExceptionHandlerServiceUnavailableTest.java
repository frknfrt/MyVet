package com.vetos.platform.web;

import com.vetos.platform.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerServiceUnavailableTest {

    static class SampleServiceUnavailableException extends DomainException {
        SampleServiceUnavailableException() {
            super("SAMPLE_UNAVAILABLE", "Servis şu an kullanılamıyor");
        }
    }

    @Test
    void should_map_ServiceUnavailableSuffix_to503() {
        var response = new GlobalExceptionHandler().handleDomainException(
            new SampleServiceUnavailableException(), new MockHttpServletRequest("POST", "/x"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().errorCode()).isEqualTo("SAMPLE_UNAVAILABLE");
    }
}
