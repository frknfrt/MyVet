package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.TrialSignupRequest;
import com.vetos.modules.platformadmin.domain.TrialSignupRequestRepository;
import com.vetos.modules.platformadmin.domain.TrialSmsPort;
import com.vetos.modules.platformadmin.domain.exception.TrialSignupRequestNotFoundException;
import com.vetos.modules.platformadmin.domain.exception.TrialSignupTooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static com.vetos.modules.platformadmin.application.TrialTestSupport.ENCODER;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.NOW;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.PHONE;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.PROPERTIES;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.clockAt;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.pendingRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResendTrialCodeUseCaseTest {

    private final UUID requestId = UUID.randomUUID();
    private final TrialSignupRequestRepository repository = mock(TrialSignupRequestRepository.class);
    private final TrialSmsPort smsPort = mock(TrialSmsPort.class);

    private ResendTrialCodeUseCase useCase(Clock clock) {
        return new ResendTrialCodeUseCase(repository, ENCODER, smsPort, () -> "654321", PROPERTIES, clock);
    }

    @Test
    void should_throwTooManyRequests_when_resendBeforeSixtySeconds() {
        when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest(requestId)));

        assertThatThrownBy(() -> useCase(clockAt(NOW.plusSeconds(59))).execute(requestId))
            .isInstanceOf(TrialSignupTooManyRequestsException.class);
        verifyNoInteractions(smsPort);
    }

    @Test
    void should_throwTooManyRequests_when_threeCodesAlreadySent() {
        TrialSignupRequest request = pendingRequest(requestId);
        request.issueCode("hash(2)", NOW.plusSeconds(60), 300);
        request.issueCode("hash(3)", NOW.plusSeconds(120), 300);
        when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> useCase(clockAt(NOW.plusSeconds(900))).execute(requestId))
            .isInstanceOf(TrialSignupTooManyRequestsException.class);
        verifyNoInteractions(smsPort);
    }

    @Test
    void should_issueNewCodeAndResetAttempts_when_resendAllowed() {
        TrialSignupRequest request = pendingRequest(requestId);
        request.registerFailedAttempt();
        when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        int cooldown = useCase(clockAt(NOW.plusSeconds(60))).execute(requestId);

        assertThat(cooldown).isEqualTo(60);
        assertThat(request.getOtpHash()).isEqualTo("hash(654321)");
        assertThat(request.getOtpAttempts()).isZero();
        assertThat(request.getOtpExpiresAt()).isEqualTo(NOW.plusSeconds(360));
        verify(repository).save(request);
        verify(smsPort).sendVerificationCode(PHONE, "654321");
    }

    @Test
    void should_throwNotFound_when_requestAlreadyCompleted() {
        TrialSignupRequest request = pendingRequest(requestId);
        request.complete(UUID.randomUUID(), NOW);
        when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> useCase(clockAt(NOW.plusSeconds(120))).execute(requestId))
            .isInstanceOf(TrialSignupRequestNotFoundException.class);
        verifyNoInteractions(smsPort);
    }

    @Test
    void should_throwNotFound_when_requestDoesNotExist() {
        when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase(clockAt(NOW)).execute(requestId))
            .isInstanceOf(TrialSignupRequestNotFoundException.class);
    }
}
