package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.exception.AiBusyServiceUnavailableException;
import com.vetos.platform.ratelimit.TenantPool;
import com.vetos.platform.ratelimit.TenantRateLimiter;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * AI cagrilari icin esZamanlilik siniri (spec 2026-10-16 §4.1): en fazla 4 cagri, 5.'si en fazla
 * 10 sn bekler, yer acilmazsa 503 AI_BUSY. Kota ayirma bu kapinin ICINDE yapilir -- yer yoksa hak dusmez.
 * Ayni sebeple TenantRateLimitFilter'in bu istek icin dustugu dakikalik AI hakki da iade edilir
 * (bu kapidan gecen uc AI ucu, filtrenin AI havuzunu harcadigi uclarla birebir ayni).
 */
@Component
public class AiCallGate {

    private final Bulkhead bulkhead;
    private final TenantRateLimiter tenantRateLimiter;

    public AiCallGate(BulkheadRegistry registry, TenantRateLimiter tenantRateLimiter) {
        this.bulkhead = registry.bulkhead("ai");
        this.tenantRateLimiter = tenantRateLimiter;
    }

    public <T> T run(UUID tenantId, Supplier<T> work) {
        try {
            return bulkhead.executeSupplier(work);
        } catch (BulkheadFullException e) {
            tenantRateLimiter.refund(TenantPool.AI, tenantId, 1);
            throw new AiBusyServiceUnavailableException();
        }
    }
}
