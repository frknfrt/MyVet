package com.vetos.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Bean adlarindaki "HealthIndicator" soneki Actuator'da atilir: efaturaHealthIndicator -> "efatura".
 * Bu adlar application.yml'deki management.endpoint.health.group.external.include listesiyle ayni olmali.
 * TARBIL bilerek yok (spec S2: gonderim hekimin tarayicisinda).
 */
@Configuration
class ObservabilityConfig {

    @Bean
    IntegrationOutcomes integrationOutcomes(MeterRegistry registry) {
        return new IntegrationOutcomes(registry, Clock.systemUTC());
    }

    @Bean
    HealthIndicator efaturaHealthIndicator(IntegrationOutcomes outcomes) {
        return () -> outcomes.health(IntegrationOutcomes.EFATURA);
    }

    @Bean
    HealthIndicator smsHealthIndicator(IntegrationOutcomes outcomes) {
        return () -> outcomes.health(IntegrationOutcomes.SMS);
    }

    @Bean
    HealthIndicator whatsappHealthIndicator(IntegrationOutcomes outcomes) {
        return () -> outcomes.health(IntegrationOutcomes.WHATSAPP);
    }

    @Bean
    HealthIndicator aiHealthIndicator(IntegrationOutcomes outcomes) {
        return () -> outcomes.health(IntegrationOutcomes.AI);
    }

    @Bean
    HealthIndicator paymentHealthIndicator(IntegrationOutcomes outcomes) {
        return () -> outcomes.health(IntegrationOutcomes.PAYMENT);
    }
}
