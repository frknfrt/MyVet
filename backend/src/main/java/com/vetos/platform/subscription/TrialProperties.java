package com.vetos.platform.subscription;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Self-servis deneme ayarlari (spec 2026-11-13). Env: VETLY_TRIAL_DAYS vb. */
@ConfigurationProperties("vetly.trial")
public record TrialProperties(
    @DefaultValue("7") int days,
    @DefaultValue("300") int otpTtlSeconds,
    @DefaultValue("5") int otpMaxAttempts,
    @DefaultValue("60") int otpResendCooldownSeconds,
    @DefaultValue("3") int otpMaxSends,
    @DefaultValue("3") int maxRequestsPerPhonePerDay
) {
}
