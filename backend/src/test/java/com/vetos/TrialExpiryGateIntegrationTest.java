package com.vetos;

import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Canli docker-compose Postgres gerekir. Spec 2026-11-13 S4.3: suresi dolan denemede erisim kapisi. */
@SpringBootTest
@AutoConfigureMockMvc
class TrialExpiryGateIntegrationTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantAdminPort tenantAdminPort;
    @Autowired private TenantLookupPort tenantLookupPort;

    private UUID newTrialTenant() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return tenantAdminPort.createTenant(
            "Kapi Test " + unique, "-", "Merkez", "-", "İzmir",
            "Ayşe Yılmaz", "kapi-" + unique + "@ornek.test", "Sifre12345!"
        );
    }

    private UUID expiredTrialTenant() {
        UUID tenantId = newTrialTenant();
        tenantAdminPort.updateSubscription(tenantId, "TRIAL", BillingStatus.TRIAL, LocalDate.now(ISTANBUL).minusDays(1));
        return tenantId;
    }

    private String bearer(UUID tenantId, String role) {
        return "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), tenantId, List.of(), role);
    }

    @Test
    void should_return402WithTrialExpiredCode_when_expiredTrialCallsPatients() throws Exception {
        UUID tenantId = expiredTrialTenant();

        mockMvc.perform(get("/api/v1/patients").header("Authorization", bearer(tenantId, "ADMIN")))
            .andExpect(status().isPaymentRequired())
            .andExpect(jsonPath("$.errorCode").value("TRIAL_EXPIRED"))
            .andExpect(jsonPath("$.message").value("Deneme kullanım süreniz dolmuştur, lütfen üyelik başlatınız."));
    }

    @Test
    void should_return200WithExpiredState_when_expiredTrialCallsSubscriptionAccess() throws Exception {
        UUID tenantId = expiredTrialTenant();

        mockMvc.perform(get("/api/v1/subscriptions/access").header("Authorization", bearer(tenantId, "VET")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("TRIAL_EXPIRED"))
            .andExpect(jsonPath("$.daysLeft").value(0));
    }

    @Test
    void should_return200_when_runningTrialCallsPatients() throws Exception {
        UUID tenantId = newTrialTenant();

        mockMvc.perform(get("/api/v1/patients").header("Authorization", bearer(tenantId, "ADMIN")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/subscriptions/access").header("Authorization", bearer(tenantId, "ADMIN")))
            .andExpect(jsonPath("$.state").value("TRIAL"))
            .andExpect(jsonPath("$.daysLeft").value(7));
    }

    @Test
    void should_return200_when_internalTenantWithPastDatesCallsPatients() throws Exception {
        UUID tenantId = newTrialTenant();
        tenantAdminPort.updateSubscription(tenantId, "INTERNAL", BillingStatus.ACTIVE, LocalDate.now(ISTANBUL).minusYears(1));

        mockMvc.perform(get("/api/v1/patients").header("Authorization", bearer(tenantId, "ADMIN")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/subscriptions/access").header("Authorization", bearer(tenantId, "ADMIN")))
            .andExpect(jsonPath("$.state").value("INTERNAL"));
    }

    @Test
    void should_unlockImmediately_when_adminExtendsExpiredTrial() throws Exception {
        UUID tenantId = expiredTrialTenant();
        mockMvc.perform(get("/api/v1/patients").header("Authorization", bearer(tenantId, "ADMIN")))
            .andExpect(status().isPaymentRequired());

        tenantAdminPort.updateSubscription(tenantId, "TRIAL", BillingStatus.TRIAL, LocalDate.now(ISTANBUL).plusDays(3));

        mockMvc.perform(get("/api/v1/patients").header("Authorization", bearer(tenantId, "ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void should_excludeExpiredTrial_from_activeTenantLookups() {
        UUID running = newTrialTenant();
        UUID expired = expiredTrialTenant();

        assertThat(tenantLookupPort.findActiveTenantIds()).contains(running).doesNotContain(expired);
        // TARBIL eklenti anahtari dogrulamasi (AuthenticateExtensionTokenUseCase) bu sorguya dayanir.
        assertThat(tenantLookupPort.isOperational(running)).isTrue();
        assertThat(tenantLookupPort.isOperational(expired)).isFalse();
    }

    @Test
    void should_hidePublicClinic_when_trialExpired() throws Exception {
        UUID running = newTrialTenant();
        UUID expired = expiredTrialTenant();
        UUID runningBranch = tenantAdminPort.findImpersonationTarget(running).orElseThrow().branchId();
        UUID expiredBranch = tenantAdminPort.findImpersonationTarget(expired).orElseThrow().branchId();

        mockMvc.perform(get("/api/v1/public/clinics/" + runningBranch)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/clinics/" + expiredBranch))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("CLINIC_NOT_AVAILABLE"));
        mockMvc.perform(get("/api/v1/public/service-types").param("tenantId", expired.toString()))
            .andExpect(status().isNotFound());
    }
}
