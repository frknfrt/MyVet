package com.vetos.platform.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfig {

    @Bean
    TenantRateLimiter tenantRateLimiter(RateLimitProperties properties) {
        return new TenantRateLimiter(properties);
    }
}
