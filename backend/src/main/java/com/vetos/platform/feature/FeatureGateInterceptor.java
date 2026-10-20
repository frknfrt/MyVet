package com.vetos.platform.feature;

import com.vetos.platform.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.UUID;

/**
 * @RequiresFeature etiketli uclarda, istegin kiracisinin paketinde ozellik kapaliysa
 * istegi controller'a ulasmadan reddeder (403 FEATURE_NOT_IN_PLAN). Etiketli uca
 * kiraci bilgisi olmadan gelinirse de reddeder -- emin olunamayan durumda acmak yerine kapat.
 * TenantContext, guvenlik filtrelerinde (JWT / eklenti anahtari) kurulur; interceptor
 * DispatcherServlet icinde, yani o filtrelerin icinde calisir.
 */
public class FeatureGateInterceptor implements HandlerInterceptor {

    private final FeatureAccessPort featureAccess;

    public FeatureGateInterceptor(FeatureAccessPort featureAccess) {
        this.featureAccess = featureAccess;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Optional<Feature> required = requiredFeature(handlerMethod);
        if (required.isEmpty()) {
            return true;
        }
        UUID tenantId = TenantContext.currentOrNull();
        if (tenantId == null || !featureAccess.isEnabled(tenantId, required.get())) {
            throw new FeatureNotInPlanForbiddenException();
        }
        return true;
    }

    public static Optional<Feature> requiredFeature(HandlerMethod handlerMethod) {
        RequiresFeature onMethod = handlerMethod.getMethodAnnotation(RequiresFeature.class);
        if (onMethod != null) {
            return Optional.of(onMethod.value());
        }
        RequiresFeature onClass = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequiresFeature.class);
        return Optional.ofNullable(onClass).map(RequiresFeature::value);
    }
}
