package com.vetos.modules.platformadmin.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrialSignupRequestTest {

    private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

    private TrialSignupRequest request() {
        return TrialSignupRequest.create(
            "Ayşe Yılmaz", "ayse@ornek.com", "+905321112233", "Pati Klinik", "İzmir", "35-1234",
            "hash(sifre)", NOW, "10.0.0.1"
        );
    }

    @Test
    void should_startPendingWithConsentRecorded_when_created() {
        TrialSignupRequest request = request();

        assertThat(request.getStatus()).isEqualTo(TrialSignupRequestStatus.PENDING);
        assertThat(request.getConsentAcceptedAt()).isEqualTo(NOW);
        assertThat(request.getConsentIp()).isEqualTo("10.0.0.1");
        assertThat(request.getOtpSendCount()).isZero();
        assertThat(request.isCompleted()).isFalse();
    }

    @Test
    void should_setExpiryAndCountSend_when_codeIssued() {
        TrialSignupRequest request = request();

        request.issueCode("hash(123456)", NOW, 300);

        assertThat(request.getOtpHash()).isEqualTo("hash(123456)");
        assertThat(request.getOtpExpiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(request.getOtpSendCount()).isEqualTo(1);
        assertThat(request.isCodeExpired(NOW.plusSeconds(300))).isFalse();
        assertThat(request.isCodeExpired(NOW.plusSeconds(301))).isTrue();
    }

    @Test
    void should_keepFailedAttempts_when_newCodeIssued() {
        TrialSignupRequest request = request();
        request.issueCode("hash(1)", NOW, 300);
        request.registerFailedAttempt();
        request.registerFailedAttempt();

        request.issueCode("hash(2)", NOW.plusSeconds(61), 300);

        assertThat(request.getOtpAttempts()).isEqualTo(2);
        assertThat(request.getOtpSendCount()).isEqualTo(2);
    }

    @Test
    void should_runOutOfAttempts_when_maxFailedAttemptsReached() {
        TrialSignupRequest request = request();
        request.issueCode("hash(1)", NOW, 300);
        for (int i = 0; i < 4; i++) {
            request.registerFailedAttempt();
        }
        assertThat(request.hasAttemptsLeft(5)).isTrue();

        request.registerFailedAttempt();

        assertThat(request.hasAttemptsLeft(5)).isFalse();
    }

    @Test
    void should_allowResendOnlyAfterCooldownAndUnderMaxSends() {
        TrialSignupRequest request = request();
        request.issueCode("hash(1)", NOW, 300);

        assertThat(request.canResend(NOW.plusSeconds(59), 60, 3)).isFalse();
        assertThat(request.canResend(NOW.plusSeconds(60), 60, 3)).isTrue();

        request.issueCode("hash(2)", NOW.plusSeconds(60), 300);
        request.issueCode("hash(3)", NOW.plusSeconds(120), 300);

        assertThat(request.canResend(NOW.plusSeconds(600), 60, 3)).isFalse();
    }

    @Test
    void should_becomeCompletedWithTenant_when_completed() {
        TrialSignupRequest request = request();
        UUID tenantId = UUID.randomUUID();

        request.complete(tenantId, NOW.plusSeconds(30));

        assertThat(request.isCompleted()).isTrue();
        assertThat(request.getStatus()).isEqualTo(TrialSignupRequestStatus.COMPLETED);
        assertThat(request.getTenantId()).isEqualTo(tenantId);
        assertThat(request.getCompletedAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(request.getOtpHash()).isNull();
    }
}
