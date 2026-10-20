package com.vetos.platform.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class OutageFailurePredicateTest {

    private final OutageFailurePredicate predicate = new OutageFailurePredicate();

    @Test
    void should_treatConnectionAndTimeoutAsOutage() {
        assertThat(predicate.test(new ResourceAccessException("timeout", new IOException("read timed out")))).isTrue();
    }

    @Test
    void should_treatOutageStatusesAsOutage() {
        assertThat(predicate.test(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "x", null, null, null))).isTrue();
        assertThat(predicate.test(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "x", null, null, null))).isTrue();
        assertThat(predicate.test(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "x", null, null, null))).isTrue();
        assertThat(predicate.test(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "x", null, null, null))).isTrue();
    }

    @Test
    void should_notTreatClientErrorsOrOtherExceptionsAsOutage() {
        assertThat(predicate.test(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "x", null, null, null))).isFalse();
        assertThat(predicate.test(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "x", null, null, null))).isFalse();
        assertThat(predicate.test(new IllegalStateException("json"))).isFalse();
        assertThat(predicate.test(CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("x")))).isFalse();
    }
}
