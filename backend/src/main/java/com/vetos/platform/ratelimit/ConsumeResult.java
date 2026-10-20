package com.vetos.platform.ratelimit;

public record ConsumeResult(boolean consumed, long retryAfterSeconds) {

    static ConsumeResult ok() {
        return new ConsumeResult(true, 0);
    }
}
