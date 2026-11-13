package com.vetos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.platformadmin.application.FlagOverdueAndSuspendUseCase;
import com.vetos.modules.platformadmin.application.GenerateDueInvoicesUseCase;
import com.vetos.modules.platformadmin.application.SuspendExpiredCancellationsUseCase;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.tenant.domain.BillingPeriod;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Canli docker-compose Postgres gerekir. Spec 2026-11-13 S4.4: denemeden ucretli pakete gecis.
 * "test" profili: iyzico kimlik bilgisi yokken odeme simulasyonuna yalnizca dev/test profilinde izin var.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrialUpgradeIntegrationTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantAdminPort tenantAdminPort;
    @Autowired private PlanRepository planRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private GenerateDueInvoicesUseCase generateDueInvoicesUseCase;
    @Autowired private FlagOverdueAndSuspendUseCase flagOverdueAndSuspendUseCase;
    @Autowired private SuspendExpiredCancellationsUseCase suspendExpiredCancellationsUseCase;

    private String planCode;
    private UUID tenantId;
    private String adminBearer;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        planCode = "TST-" + unique;
        Plan plan = Plan.create(planCode, "Test Paketi " + unique, new BigDecimal("1490.00"));
        plan.updateDetails("Test Paketi " + unique, new BigDecimal("1490.00"), new BigDecimal("14900.00"), null, null, null, List.of(), Set.of());
        planRepository.save(plan);

        tenantId = tenantAdminPort.createTenant(
            "Gecis Test " + unique, "-", "Merkez", "-", "İzmir",
            "Ayşe Yılmaz", "gecis-" + unique.toLowerCase() + "@ornek.test", "Sifre12345!"
        );
        // Deneme suresi dolmus: panel kilitli.
        tenantAdminPort.updateSubscription(tenantId, "TRIAL", BillingStatus.TRIAL, LocalDate.now(ISTANBUL).minusDays(1));
        adminBearer = "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), tenantId, List.of(), "ADMIN");
    }

    private String startCheckout(BillingPeriod period) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/subscriptions/upgrade-checkout")
                .header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("planCode", planCode, "billingPeriod", period.name()))))
            .andExpect(status().isOk())
            .andReturn();
        String url = objectMapper.readTree(result.getResponse().getContentAsString()).get("checkoutFormUrl").asText();
        assertThat(url).contains("/api/v1/public/payments/iyzico/callback?token=");
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    private int invoiceCount() {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from platform_invoices where tenant_id = ?", Integer.class, tenantId);
        return count == null ? 0 : count;
    }

    @Test
    void should_unlockPanel_when_expiredTrialPays() throws Exception {
        mockMvc.perform(get("/api/v1/patients").header("Authorization", adminBearer))
            .andExpect(status().isPaymentRequired());

        String token = startCheckout(BillingPeriod.MONTHLY);
        mockMvc.perform(get("/api/v1/public/payments/iyzico/callback").param("token", token))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/ayarlar/abonelik?odeme=basarili")));

        mockMvc.perform(get("/api/v1/patients").header("Authorization", adminBearer))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/subscriptions/access").header("Authorization", adminBearer))
            .andExpect(jsonPath("$.state").value("ACTIVE"));

        TenantAdminOverview overview = tenantAdminPort.getOverview(tenantId);
        assertThat(overview.planCode()).isEqualTo(planCode);
        assertThat(overview.billingStatus()).isEqualTo(BillingStatus.ACTIVE);
        assertThat(overview.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(overview.renewsAt()).isAfter(LocalDate.now(ISTANBUL).plusDays(27));

        Map<String, Object> invoice = jdbcTemplate.queryForMap(
            "select status, amount, plan_code from platform_invoices where tenant_id = ?", tenantId);
        assertThat(invoice.get("status")).isEqualTo("PAID");
        assertThat((BigDecimal) invoice.get("amount")).isEqualByComparingTo("1490.00");
        assertThat(invoice.get("plan_code")).isEqualTo(planCode);
    }

    @Test
    void should_keepSingleInvoice_when_callbackRepeated() throws Exception {
        String token = startCheckout(BillingPeriod.ANNUAL);

        mockMvc.perform(get("/api/v1/public/payments/iyzico/callback").param("token", token)).andExpect(status().isFound());
        mockMvc.perform(get("/api/v1/public/payments/iyzico/callback").param("token", token))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("?odeme=basarili")));

        assertThat(invoiceCount()).isEqualTo(1);
        assertThat(tenantAdminPort.getOverview(tenantId).renewsAt()).isAfter(LocalDate.now(ISTANBUL).plusMonths(11));
    }

    @Test
    void should_leaveNoOpenInvoice_when_checkoutAbandoned() throws Exception {
        startCheckout(BillingPeriod.MONTHLY);

        assertThat(invoiceCount()).isZero();
        TenantAdminOverview overview = tenantAdminPort.getOverview(tenantId);
        assertThat(overview.planCode()).isEqualTo("TRIAL");
        assertThat(overview.billingStatus()).isEqualTo(BillingStatus.TRIAL);
    }

    @Test
    void should_notSuspendOrFlagTrial_when_dailyBillingRunsAfterAbandonedCheckout() throws Exception {
        startCheckout(BillingPeriod.MONTHLY);
        LocalDate later = LocalDate.now(ISTANBUL).plusDays(45);

        generateDueInvoicesUseCase.execute(later);
        flagOverdueAndSuspendUseCase.execute(later);
        suspendExpiredCancellationsUseCase.execute(later);

        TenantAdminOverview overview = tenantAdminPort.getOverview(tenantId);
        assertThat(overview.billingStatus()).isEqualTo(BillingStatus.TRIAL);
        assertThat(overview.status()).isNotEqualTo(TenantStatus.SUSPENDED);
        assertThat(invoiceCount()).isZero();
    }

    @Test
    void should_return403_when_nonAdminStartsCheckout() throws Exception {
        String vetBearer = "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), tenantId, List.of(), "VET");

        mockMvc.perform(post("/api/v1/subscriptions/upgrade-checkout")
                .header("Authorization", vetBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("planCode", planCode, "billingPeriod", "MONTHLY"))))
            .andExpect(status().isForbidden());
    }
}
