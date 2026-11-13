package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.TrialEmailPort;
import com.vetos.modules.platformadmin.domain.TrialSignupRequest;
import com.vetos.modules.platformadmin.domain.TrialSignupRequestRepository;
import com.vetos.modules.platformadmin.domain.TrialSignupRequestStatus;
import com.vetos.modules.platformadmin.domain.TrialSmsPort;
import com.vetos.modules.platformadmin.domain.exception.SignupEmailAlreadyRegisteredConflictException;
import com.vetos.modules.platformadmin.domain.exception.TrialCodeExpiredException;
import com.vetos.modules.platformadmin.domain.exception.TrialCodeInvalidException;
import com.vetos.modules.platformadmin.domain.exception.TrialPhoneAlreadyUsedConflictException;
import com.vetos.modules.platformadmin.domain.exception.TrialSignupRequestNotFoundException;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static com.vetos.modules.platformadmin.application.TrialTestSupport.CLOCK;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.ENCODER;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.NOW;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.PHONE;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.PROPERTIES;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.clockAt;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.pendingRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VerifyTrialSignupUseCaseTest {

    private static final String LOGIN_URL = "http://localhost:5173/login?email=ayse%40ornek.com&deneme=1";
    /** NOW = 2026-11-13 (Istanbul), 7 gunluk deneme. */
    private static final LocalDate TRIAL_ENDS = LocalDate.of(2026, 11, 20);

    private final UUID requestId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final TrialSignupRequestRepository repository = mock(TrialSignupRequestRepository.class);
    private final TenantAdminPort tenantAdminPort = mock(TenantAdminPort.class);
    private final TrialSmsPort smsPort = mock(TrialSmsPort.class);
    private final TrialEmailPort emailPort = mock(TrialEmailPort.class);

    private TrialSignupRequest request;

    private VerifyTrialSignupUseCase useCase(Clock clock) {
        return new VerifyTrialSignupUseCase(
            repository, tenantAdminPort, ENCODER, smsPort, emailPort, PROPERTIES, "http://localhost:5173", clock
        );
    }

    @BeforeEach
    void setUp() {
        request = pendingRequest(requestId);
        when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(tenantAdminPort.createTrialTenant(any(), any(), any(), any(), any(), any(), any())).thenReturn(tenantId);
    }

    @Test
    void should_createTenantAndReturnLoginUrl_when_codeMatches() {
        String loginUrl = useCase(CLOCK).execute(requestId, "123456");

        assertThat(loginUrl).isEqualTo(LOGIN_URL);
        verify(tenantAdminPort).createTrialTenant(
            "Pati Klinik", "İzmir", "Ayşe Yılmaz", "ayse@ornek.com", "hash(Sifre12345)", PHONE, "35-1234"
        );
        assertThat(request.getStatus()).isEqualTo(TrialSignupRequestStatus.COMPLETED);
        assertThat(request.getTenantId()).isEqualTo(tenantId);
        verify(repository).save(request);
    }

    @Test
    void should_sendWelcomeSmsAndEmail_when_tenantCreated() {
        useCase(CLOCK).execute(requestId, "123456");

        verify(smsPort).sendWelcome(PHONE, "Pati Klinik", TRIAL_ENDS, "http://localhost:5173/login");
        verify(emailPort).sendWelcome("ayse@ornek.com", "Ayşe Yılmaz", "Pati Klinik", TRIAL_ENDS, LOGIN_URL);
    }

    @Test
    void should_throwInvalidAndCountAttempt_when_codeWrong() {
        assertThatThrownBy(() -> useCase(CLOCK).execute(requestId, "000000"))
            .isInstanceOf(TrialCodeInvalidException.class)
            .hasMessageContaining("4");

        assertThat(request.getOtpAttempts()).isEqualTo(1);
        verify(repository).save(request);
        verify(tenantAdminPort, never()).createTrialTenant(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void should_throwInvalid_when_fiveAttemptsUsedEvenIfCodeCorrect() {
        for (int i = 0; i < 5; i++) {
            request.registerFailedAttempt();
        }

        assertThatThrownBy(() -> useCase(CLOCK).execute(requestId, "123456"))
            .isInstanceOf(TrialCodeInvalidException.class);
        verify(tenantAdminPort, never()).createTrialTenant(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void should_throwExpired_when_codeOlderThanFiveMinutes() {
        assertThatThrownBy(() -> useCase(clockAt(NOW.plusSeconds(301))).execute(requestId, "123456"))
            .isInstanceOf(TrialCodeExpiredException.class);
        verify(tenantAdminPort, never()).createTrialTenant(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void should_returnLoginUrlWithoutCreatingAgain_when_requestAlreadyCompleted() {
        request.complete(tenantId, NOW);

        String loginUrl = useCase(CLOCK).execute(requestId, "yanlis");

        assertThat(loginUrl).isEqualTo(LOGIN_URL);
        verify(tenantAdminPort, never()).createTrialTenant(any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(smsPort, emailPort);
    }

    @Test
    void should_throwConflict_when_emailRegisteredBetweenStartAndVerify() {
        when(tenantAdminPort.isEmailRegistered("ayse@ornek.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase(CLOCK).execute(requestId, "123456"))
            .isInstanceOf(SignupEmailAlreadyRegisteredConflictException.class);
        verify(tenantAdminPort, never()).createTrialTenant(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void should_throwConflict_when_phoneCompletedBetweenStartAndVerify() {
        when(repository.existsByPhoneAndStatus(PHONE, TrialSignupRequestStatus.COMPLETED)).thenReturn(true);

        assertThatThrownBy(() -> useCase(CLOCK).execute(requestId, "123456"))
            .isInstanceOf(TrialPhoneAlreadyUsedConflictException.class);
        verify(tenantAdminPort, never()).createTrialTenant(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void should_stillComplete_when_welcomeSmsFails() {
        doThrow(new RuntimeException("sms saglayicisi cevap vermedi"))
            .when(smsPort).sendWelcome(anyString(), anyString(), any(), anyString());

        String loginUrl = useCase(CLOCK).execute(requestId, "123456");

        assertThat(loginUrl).isEqualTo(LOGIN_URL);
        assertThat(request.isCompleted()).isTrue();
        verify(emailPort).sendWelcome(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void should_throwNotFound_when_requestDoesNotExist() {
        UUID unknown = UUID.randomUUID();
        when(repository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase(CLOCK).execute(unknown, "123456"))
            .isInstanceOf(TrialSignupRequestNotFoundException.class);
    }
}
