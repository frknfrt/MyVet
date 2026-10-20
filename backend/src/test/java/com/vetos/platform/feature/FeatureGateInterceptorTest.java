package com.vetos.platform.feature;

import com.vetos.platform.tenancy.TenantContext;
import com.vetos.platform.web.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeatureGateInterceptorTest {

    private static final UUID TENANT = UUID.randomUUID();

    private final Set<Feature> enabled = EnumSet.noneOf(Feature.class);

    private final FeatureAccessPort featureAccess = new FeatureAccessPort() {
        @Override
        public boolean isEnabled(UUID tenantId, Feature feature) {
            return TENANT.equals(tenantId) && enabled.contains(feature);
        }

        @Override
        public Set<Feature> enabledFeatures(UUID tenantId) {
            return enabled;
        }

        @Override
        public boolean enforcementActive() {
            return true;
        }
    };

    @RestController
    @RequiresFeature(Feature.LAB_INTEGRATION)
    static class LabProbe {
        @GetMapping("/probe/lab")
        String lab() {
            return "ok";
        }

        @RequiresFeature(Feature.INVENTORY)
        @GetMapping("/probe/lab/stock")
        String stock() {
            return "ok";
        }
    }

    @RestController
    static class FreeProbe {
        @GetMapping("/probe/free")
        String free() {
            return "ok";
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LabProbe(), new FreeProbe())
        .setControllerAdvice(new GlobalExceptionHandler())
        .addInterceptors(new FeatureGateInterceptor(featureAccess))
        .build();

    @BeforeEach
    void setTenant() {
        TenantContext.set(TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void should_allow_when_handlerHasNoAnnotation() throws Exception {
        mockMvc.perform(get("/probe/free")).andExpect(status().isOk());
    }

    @Test
    void should_allow_when_featureEnabled() throws Exception {
        enabled.add(Feature.LAB_INTEGRATION);

        mockMvc.perform(get("/probe/lab")).andExpect(status().isOk());
    }

    @Test
    void should_return403_when_featureDisabled() throws Exception {
        mockMvc.perform(get("/probe/lab"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("FEATURE_NOT_IN_PLAN"));
    }

    @Test
    void should_useMethodAnnotation_overClassAnnotation() throws Exception {
        enabled.add(Feature.LAB_INTEGRATION);
        mockMvc.perform(get("/probe/lab/stock")).andExpect(status().isForbidden());

        enabled.clear();
        enabled.add(Feature.INVENTORY);
        mockMvc.perform(get("/probe/lab/stock")).andExpect(status().isOk());
    }

    @Test
    void should_return403_when_noTenantOnAnnotatedEndpoint() throws Exception {
        enabled.addAll(EnumSet.allOf(Feature.class));
        TenantContext.clear();

        mockMvc.perform(get("/probe/lab"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("FEATURE_NOT_IN_PLAN"));
    }
}
