package com.vetos.platform.feature;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Canli docker-compose Postgres gerekir (diger @SpringBootTest'ler gibi). Hangi ucun hangi
 * ozellige bagli oldugunu gercek handler mapping uzerinden dogrular -- etiket unutulursa kirilir.
 */
@SpringBootTest
class FeatureGateWiringTest {

    private static final Set<String> TARBIL_STOCK_PATTERNS = Set.of(
        "/api/v1/tarbil/stock-sync",
        "/api/v1/tarbil/stock-sync/{snapshotId}/apply",
        "/api/v1/tarbil/encounters/{encounterId}/deduction",
        "/api/v1/tarbil-extension/stock-snapshots",
        "/api/v1/tarbil-extension/stock-snapshots/compare"
    );

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapping;

    private List<Map.Entry<RequestMappingInfo, HandlerMethod>> handlersWhere(Predicate<String> patternMatches) {
        return mapping.getHandlerMethods().entrySet().stream()
            .filter(e -> e.getKey().getPatternValues().stream().anyMatch(patternMatches))
            .toList();
    }

    private static Predicate<String> under(String prefix) {
        return p -> p.equals(prefix) || p.startsWith(prefix + "/");
    }

    private void assertAllRequire(String prefix, Feature expected) {
        var handlers = handlersWhere(under(prefix));
        assertThat(handlers).as(prefix).isNotEmpty();
        handlers.forEach(e -> assertThat(FeatureGateInterceptor.requiredFeature(e.getValue()))
            .as(e.getKey().toString()).contains(expected));
    }

    @Test
    void should_gateWholeModuleControllers() {
        assertAllRequire("/api/v1/ai", Feature.AI_ASSISTANT);
        assertAllRequire("/api/v1/imaging-records", Feature.IMAGING);
        assertAllRequire("/api/v1/boarding-rooms", Feature.BOARDING);
        assertAllRequire("/api/v1/boarding-stays", Feature.BOARDING);
        assertAllRequire("/api/v1/inventory-items", Feature.INVENTORY);
        assertAllRequire("/api/v1/inventory/pending-deductions", Feature.INVENTORY);
        assertAllRequire("/api/v1/efatura", Feature.E_FATURA);
    }

    @Test
    void should_gateLabArchiveButNotQuickEvaluate() {
        var evaluateHandlers = handlersWhere(p -> p.equals("/api/v1/lab-results/evaluate"));
        assertThat(evaluateHandlers).isNotEmpty();
        evaluateHandlers.forEach(e -> assertThat(FeatureGateInterceptor.requiredFeature(e.getValue()))
            .as(e.getKey().toString()).isEmpty());

        var archiveHandlers = handlersWhere(p -> p.startsWith("/api/v1/lab-results")
            && !p.equals("/api/v1/lab-results/evaluate"));
        assertThat(archiveHandlers).isNotEmpty();
        archiveHandlers.forEach(e -> assertThat(FeatureGateInterceptor.requiredFeature(e.getValue()))
            .as(e.getKey().toString()).contains(Feature.LAB_INTEGRATION));
    }

    @Test
    void should_gateOnlyStockEndpoints_inTarbilAndBilling() {
        assertAllRequire("/api/v1/invoices/quick-sale", Feature.INVENTORY);
        TARBIL_STOCK_PATTERNS.forEach(p -> assertAllRequire(p, Feature.INVENTORY));

        var otherTarbil = handlersWhere(p -> (p.startsWith("/api/v1/tarbil/") || p.startsWith("/api/v1/tarbil-extension/"))
            && !TARBIL_STOCK_PATTERNS.contains(p));
        assertThat(otherTarbil).isNotEmpty();
        otherTarbil.forEach(e -> assertThat(FeatureGateInterceptor.requiredFeature(e.getValue()))
            .as(e.getKey().toString()).isEmpty());
    }

    @Test
    void should_notGatePublicOrPlatformAdminEndpoints() {
        var handlers = handlersWhere(p -> p.startsWith("/api/v1/public/") || p.startsWith("/api/v1/platform-admin/"));
        assertThat(handlers).isNotEmpty();
        handlers.forEach(e -> assertThat(FeatureGateInterceptor.requiredFeature(e.getValue()))
            .as(e.getKey().toString()).isEmpty());
    }

    @Test
    void should_registerFeatureGateInterceptor() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/subscriptions/features");
        ServletRequestPathUtils.parseAndCache(request);

        HandlerExecutionChain chain = mapping.getHandler(request);

        assertThat(chain).isNotNull();
        assertThat(chain.getInterceptorList()).anyMatch(FeatureGateInterceptor.class::isInstance);
    }
}
