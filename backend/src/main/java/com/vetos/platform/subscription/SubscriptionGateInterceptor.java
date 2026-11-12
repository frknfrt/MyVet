package com.vetos.platform.subscription;

import com.vetos.platform.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.UUID;

/**
 * Deneme suresi dolmus kiracinin isteklerini 402 TRIAL_EXPIRED ile reddeder. Giris ve
 * abonelik/odeme uclari acik kalir ki kullanici paket secip odeyebilsin. TenantContext
 * yoksa (public uclar, platform admin) devreye girmez.
 */
public class SubscriptionGateInterceptor implements HandlerInterceptor {

    private static final List<String> ALLOWED_PREFIXES = List.of("/api/v1/auth", "/api/v1/subscriptions");

    private final SubscriptionAccessPort subscriptionAccess;

    public SubscriptionGateInterceptor(SubscriptionAccessPort subscriptionAccess) {
        this.subscriptionAccess = subscriptionAccess;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UUID tenantId = TenantContext.currentOrNull();
        if (tenantId == null || isAllowed(request.getRequestURI())) {
            return true;
        }
        if (subscriptionAccess.accessOf(tenantId).isTrialExpired()) {
            throw new TrialExpiredPaymentRequiredException();
        }
        return true;
    }

    private static boolean isAllowed(String path) {
        for (String prefix : ALLOWED_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
}
