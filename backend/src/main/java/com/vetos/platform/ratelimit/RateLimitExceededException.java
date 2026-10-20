package com.vetos.platform.ratelimit;

import com.vetos.platform.exception.DomainException;

/** GlobalExceptionHandler'da ozel handler: 429 + Retry-After. */
public class RateLimitExceededException extends DomainException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("RATE_LIMITED", RateLimitResponses.message(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
