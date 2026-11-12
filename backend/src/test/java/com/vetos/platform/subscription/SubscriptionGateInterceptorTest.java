package com.vetos.platform.subscription;

import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SubscriptionGateInterceptorTest {

    private static final UUID TENANT = UUID.randomUUID();

    private final SubscriptionAccessPort accessPort = mock(SubscriptionAccessPort.class);
    private final SubscriptionGateInterceptor interceptor = new SubscriptionGateInterceptor(accessPort);

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private boolean preHandle(String path) {
        return interceptor.preHandle(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), new Object());
    }

    private void expiredTenant() {
        TenantContext.set(TENANT);
        when(accessPort.accessOf(TENANT)).thenReturn(
            new SubscriptionAccess(SubscriptionAccessState.TRIAL_EXPIRED, LocalDate.of(2026, 11, 1), 0));
    }

    @Test
    void should_throwPaymentRequired_when_trialExpiredAndPathIsProtected() {
        expiredTenant();

        assertThatThrownBy(() -> preHandle("/api/v1/patients"))
            .isInstanceOf(TrialExpiredPaymentRequiredException.class)
            .hasMessage("Deneme kullanım süreniz dolmuştur, lütfen üyelik başlatınız.");
    }

    @Test
    void should_pass_when_trialExpiredAndPathIsSubscriptions() {
        expiredTenant();

        assertThat(preHandle("/api/v1/subscriptions/access")).isTrue();
    }

    @Test
    void should_pass_when_trialExpiredAndPathIsAuth() {
        expiredTenant();

        assertThat(preHandle("/api/v1/auth/logout")).isTrue();
    }

    @Test
    void should_notTreatLookalikePathAsAllowed_when_trialExpired() {
        expiredTenant();

        assertThatThrownBy(() -> preHandle("/api/v1/subscriptions-export"))
            .isInstanceOf(TrialExpiredPaymentRequiredException.class);
    }

    @Test
    void should_pass_when_trialStillRunning() {
        TenantContext.set(TENANT);
        when(accessPort.accessOf(TENANT)).thenReturn(
            new SubscriptionAccess(SubscriptionAccessState.TRIAL, LocalDate.of(2026, 11, 20), 3));

        assertThat(preHandle("/api/v1/patients")).isTrue();
    }

    @Test
    void should_pass_when_noTenantContext() {
        assertThat(preHandle("/api/v1/platform-admin/tenants")).isTrue();
        verifyNoInteractions(accessPort);
    }
}
