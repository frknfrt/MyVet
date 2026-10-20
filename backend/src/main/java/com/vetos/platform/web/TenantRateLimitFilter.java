package com.vetos.platform.web;

import com.vetos.platform.ratelimit.ConsumeResult;
import com.vetos.platform.ratelimit.RateLimitResponses;
import com.vetos.platform.ratelimit.TenantPool;
import com.vetos.platform.ratelimit.TenantRateLimiter;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Kimlik dogrulanmis trafik icin kiraci basina koruma sinirlari (spec 2026-10-15 §1.3).
 * Kimlik dogrulama filtresinden SONRA calisir (TenantContext dolu); kiracisiz istekler
 * (anonim, platform admin, gecersiz token) dokunulmadan gecer.
 */
public class TenantRateLimitFilter extends OncePerRequestFilter {

    static final Set<String> AI_GENERATION_PATHS = Set.of(
        "/api/v1/ai/soap-drafts",
        "/api/v1/ai/treatment-recommendations",
        "/api/v1/ai/diagnosis-suggestions"
    );

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    private final TenantRateLimiter limiter;

    public TenantRateLimitFilter(TenantRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {
        UUID tenantId = TenantContext.currentOrNull();
        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }
        // Decode edilmis, uygulama-goreli path (RateLimitFilter ile ayni gerekce: percent-encoding bypass'i).
        String path = PATH_HELPER.getPathWithinApplication(request);

        ConsumeResult general = limiter.tryConsume(TenantPool.GENERAL, tenantId, 1);
        if (!general.consumed()) {
            RateLimitResponses.write(response, general.retryAfterSeconds(), path);
            return;
        }
        if ("POST".equals(request.getMethod()) && AI_GENERATION_PATHS.contains(path)) {
            ConsumeResult ai = limiter.tryConsume(TenantPool.AI, tenantId, 1);
            if (!ai.consumed()) {
                RateLimitResponses.write(response, ai.retryAfterSeconds(), path);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
