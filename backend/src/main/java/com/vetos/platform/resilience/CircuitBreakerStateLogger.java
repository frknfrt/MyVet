package com.vetos.platform.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Tum devre kesicilerin durum degisimini loglar (spec §4.2): ACIK -> WARN, KAPALI -> INFO. */
@Component
class CircuitBreakerStateLogger {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerStateLogger.class);

    CircuitBreakerStateLogger(CircuitBreakerRegistry registry) {
        registry.getAllCircuitBreakers().forEach(this::attach);
        registry.getEventPublisher().onEntryAdded(event -> attach(event.getAddedEntry()));
    }

    private void attach(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.State to = event.getStateTransition().getToState();
            if (to == CircuitBreaker.State.OPEN || to == CircuitBreaker.State.FORCED_OPEN) {
                log.warn("Devre kesici ACIK: {} (sonraki deneme {} sn sonra)", circuitBreaker.getName(),
                    circuitBreaker.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1) / 1000);
            } else if (to == CircuitBreaker.State.CLOSED) {
                log.info("Devre kesici kapandi: {}", circuitBreaker.getName());
            }
        });
    }
}
