package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.TrialOtpGenerator;
import com.vetos.modules.platformadmin.domain.TrialSignupRequest;
import com.vetos.modules.platformadmin.domain.TrialSignupRequestRepository;
import com.vetos.modules.platformadmin.domain.TrialSignupRequestStatus;
import com.vetos.modules.platformadmin.domain.TrialSmsPort;
import com.vetos.modules.platformadmin.domain.exception.SignupEmailAlreadyRegisteredConflictException;
import com.vetos.modules.platformadmin.domain.exception.TrialPhoneAlreadyUsedConflictException;
import com.vetos.modules.platformadmin.domain.exception.TrialPhoneInvalidException;
import com.vetos.modules.platformadmin.domain.exception.TrialSignupTooManyRequestsException;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;

import static com.vetos.modules.platformadmin.application.TrialTestSupport.CLOCK;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.ENCODER;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.NOW;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.PHONE;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StartTrialSignupUseCaseTest {

    private final TrialSignupRequestRepository repository = mock(TrialSignupRequestRepository.class);
    private final TenantAdminPort tenantAdminPort = mock(TenantAdminPort.class);
    private final TrialSmsPort smsPort = mock(TrialSmsPort.class);
    private final TrialOtpGenerator otpGenerator = () -> "123456";

    private final StartTrialSignupUseCase useCase = new StartTrialSignupUseCase(
        repository, tenantAdminPort, ENCODER, smsPort, otpGenerator, PROPERTIES, CLOCK
    );

    private final UUID savedId = UUID.randomUUID();

    @BeforeEach
    void saveAssignsId() {
        when(repository.save(any())).thenAnswer(invocation -> {
            TrialSignupRequest request = invocation.getArgument(0);
            ReflectionTestUtils.setField(request, "id", savedId);
            return request;
        });
    }

    private StartTrialSignupCommand command(String phone, String email) {
        return new StartTrialSignupCommand(
            "Ayşe Yılmaz", phone, email, "Pati Klinik", "İzmir", "35-1234", "Sifre12345", true, "10.0.0.1"
        );
    }

    @Test
    void should_saveRequestAndSendCode_when_validCommand() {
        TrialSignupStarted started = useCase.execute(command("0532 111 22 33", " Ayse@Ornek.com "));

        assertThat(started.requestId()).isEqualTo(savedId);
        assertThat(started.resendAvailableInSeconds()).isEqualTo(60);
        verify(smsPort).sendVerificationCode(PHONE, "123456");

        ArgumentCaptor<TrialSignupRequest> saved = ArgumentCaptor.forClass(TrialSignupRequest.class);
        verify(repository).save(saved.capture());
        TrialSignupRequest request = saved.getValue();
        assertThat(request.getPhone()).isEqualTo(PHONE);
        assertThat(request.getEmail()).isEqualTo("ayse@ornek.com");
        assertThat(request.getCity()).isEqualTo("İzmir");
        assertThat(request.getChamberRegistrationNo()).isEqualTo("35-1234");
        assertThat(request.getStatus()).isEqualTo(TrialSignupRequestStatus.PENDING);
        assertThat(request.getOtpExpiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(request.getConsentIp()).isEqualTo("10.0.0.1");
    }

    @Test
    void should_storeOnlyHashes_when_requestSaved() {
        useCase.execute(command("0532 111 22 33", "ayse@ornek.com"));

        ArgumentCaptor<TrialSignupRequest> saved = ArgumentCaptor.forClass(TrialSignupRequest.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash(Sifre12345)");
        assertThat(saved.getValue().getOtpHash()).isEqualTo("hash(123456)");
    }

    @Test
    void should_throwConflict_when_emailAlreadyRegistered() {
        when(tenantAdminPort.isEmailRegistered("ayse@ornek.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(command("0532 111 22 33", "ayse@ornek.com")))
            .isInstanceOf(SignupEmailAlreadyRegisteredConflictException.class);
        verifyNoInteractions(smsPort);
        verify(repository, never()).save(any());
    }

    @Test
    void should_throwConflict_when_phoneHasCompletedTrial() {
        when(repository.existsByPhoneAndStatus(PHONE, TrialSignupRequestStatus.COMPLETED)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(command("0532 111 22 33", "ayse@ornek.com")))
            .isInstanceOf(TrialPhoneAlreadyUsedConflictException.class);
        verifyNoInteractions(smsPort);
    }

    @Test
    void should_throwTooManyRequests_when_phoneHasThreeRequestsInLast24h() {
        when(repository.countByPhoneAndCreatedAtAfter(PHONE, NOW.minus(Duration.ofHours(24)))).thenReturn(3L);

        assertThatThrownBy(() -> useCase.execute(command("0532 111 22 33", "ayse@ornek.com")))
            .isInstanceOf(TrialSignupTooManyRequestsException.class);
        verifyNoInteractions(smsPort);
    }

    @Test
    void should_allowRequest_when_phoneHasTwoRequestsInLast24h() {
        when(repository.countByPhoneAndCreatedAtAfter(PHONE, NOW.minus(Duration.ofHours(24)))).thenReturn(2L);

        useCase.execute(command("0532 111 22 33", "ayse@ornek.com"));

        verify(smsPort).sendVerificationCode(PHONE, "123456");
    }

    @Test
    void should_throwInvalidPhone_when_phoneIsNotTurkishMobile() {
        assertThatThrownBy(() -> useCase.execute(command("0212 111 22 33", "ayse@ornek.com")))
            .isInstanceOf(TrialPhoneInvalidException.class);
        verifyNoInteractions(smsPort);
        verify(repository, never()).save(any());
    }
}
