package com.vetos.platform.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Kiraci basina koruma sinirlari (spec 2026-10-15 §1.1). Herkese ayni; paket kotalari ayri (platform.quota). */
@ConfigurationProperties("vetly.rate-limit")
public record RateLimitProperties(
    @DefaultValue("600") int tenantGeneralPerMinute,
    @DefaultValue("10") int tenantAiPerMinute,
    @DefaultValue("2000") int campaignRecipientsPerHour
) {}
