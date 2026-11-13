package com.vetos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.platformadmin.domain.TrialOtpGenerator;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Canli docker-compose Postgres gerekir. Spec 2026-11-13 S3.1: vitrinden self-servis deneme kaydi. */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test") // SMS simulasyonu yalniz dev/test profilinde
class TrialSignupIntegrationTest {

    private static final String PASSWORD = "Sifre12345!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantAdminPort tenantAdminPort;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private TrialOtpGenerator otpGenerator;

    private String email;
    private String phone;
    private RequestPostProcessor fromUniqueIp;

    @BeforeEach
    void setUp() {
        when(otpGenerator.next()).thenReturn("123456");
        String unique = UUID.randomUUID().toString().substring(0, 8);
        email = "deneme-" + unique + "@ornek.test";
        phone = "05" + String.format("%09d", ThreadLocalRandom.current().nextInt(300_000_000, 599_999_999));
        // RateLimitFilter IP basina sayar; testler birbirinin kotasini yemesin.
        String ip = "10.9." + ThreadLocalRandom.current().nextInt(1, 250) + "." + ThreadLocalRandom.current().nextInt(1, 250);
        fromUniqueIp = request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private UUID start() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/public/signup/trial/start").with(fromUniqueIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                    "fullName", "Ayşe Yılmaz", "phone", phone, "email", email, "clinicName", "Pati Klinik",
                    "city", "İzmir", "chamberRegistrationNo", "35-1234", "password", PASSWORD, "consentAccepted", true
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resendAvailableInSeconds").value(60))
            .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("requestId").asText());
    }

    private MvcResult verify(UUID requestId, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/public/signup/trial/verify").with(fromUniqueIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("requestId", requestId.toString(), "code", code))))
            .andReturn();
    }

    private UUID tenantIdOf(UUID requestId) {
        return jdbcTemplate.queryForObject(
            "select tenant_id from trial_signup_requests where id = ?", UUID.class, requestId);
    }

    @Test
    void should_allowLoginWithChosenPassword_when_signupVerified() throws Exception {
        UUID requestId = start();

        MvcResult verified = verify(requestId, "123456");

        assertThat(verified.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(verified.getResponse().getContentAsString());
        assertThat(body.get("loginUrl").asText()).contains("/login?email=").endsWith("&deneme=1");

        mockMvc.perform(post("/api/v1/auth/login").with(fromUniqueIp)
                .header("X-Vetly-Client", "web")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void should_startSevenDayTrial_when_signupVerified() throws Exception {
        UUID requestId = start();
        verify(requestId, "123456");

        TenantAdminOverview overview = tenantAdminPort.getOverview(tenantIdOf(requestId));

        assertThat(overview.name()).isEqualTo("Pati Klinik");
        assertThat(overview.planCode()).isEqualTo("TRIAL");
        assertThat(overview.billingStatus()).isEqualTo(BillingStatus.TRIAL);
        // Hos geldiniz mesajindaki bitis tarihiyle ayni gun: Istanbul takvimine gore bugun + 7.
        assertThat(overview.renewsAt()).isEqualTo(LocalDate.now(ZoneId.of("Europe/Istanbul")).plusDays(7));
        assertThat(overview.staffUserCount()).isEqualTo(1);
        // Gizli alanlar kayit tamamlaninca silinir.
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "select status, password_hash, otp_hash from trial_signup_requests where id = ?", requestId);
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat(row.get("password_hash")).isNull();
        assertThat(row.get("otp_hash")).isNull();
    }

    @Test
    void should_createSingleTenant_when_verifyCalledTwiceConcurrently() throws Exception {
        UUID requestId = start();
        Callable<Integer> call = () -> verify(requestId, "123456").getResponse().getStatus();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = pool.invokeAll(List.of(call, call));
            assertThat(results.get(0).get()).isEqualTo(200);
            assertThat(results.get(1).get()).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        Integer staffWithEmail = jdbcTemplate.queryForObject(
            "select count(*) from staff_users where email = ?", Integer.class, email);
        assertThat(staffWithEmail).isEqualTo(1);
    }

    @Test
    void should_persistFailedAttempt_when_wrongCodeSubmitted() throws Exception {
        UUID requestId = start();

        MvcResult wrong = verify(requestId, "000000");

        assertThat(wrong.getResponse().getStatus()).isEqualTo(422);
        assertThat(objectMapper.readTree(wrong.getResponse().getContentAsString()).get("errorCode").asText())
            .isEqualTo("TRIAL_CODE_INVALID");
        Integer attempts = jdbcTemplate.queryForObject(
            "select otp_attempts from trial_signup_requests where id = ?", Integer.class, requestId);
        assertThat(attempts).isEqualTo(1);
        assertThat(tenantIdOf(requestId)).isNull();
    }

    @Test
    void should_rejectSecondTrial_when_phoneAlreadyCompletedOne() throws Exception {
        UUID requestId = start();
        verify(requestId, "123456");

        mockMvc.perform(post("/api/v1/public/signup/trial/start").with(fromUniqueIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                    "fullName", "Ayşe Yılmaz", "phone", "+90 " + phone.substring(1), "email", "baska-" + email,
                    "clinicName", "Pati Klinik 2", "city", "İzmir", "chamberRegistrationNo", "35-1234",
                    "password", PASSWORD, "consentAccepted", true
                ))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("TRIAL_PHONE_ALREADY_USED"));
    }

    @Test
    void should_return400_when_consentNotAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/public/signup/trial/start").with(fromUniqueIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                    "fullName", "Ayşe Yılmaz", "phone", phone, "email", email, "clinicName", "Pati Klinik",
                    "city", "İzmir", "chamberRegistrationNo", "35-1234", "password", PASSWORD, "consentAccepted", false
                ))))
            .andExpect(status().isBadRequest());
    }
}
