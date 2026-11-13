package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.TrialOtpGenerator;
import com.vetos.modules.platformadmin.domain.TrialSignupRequest;
import com.vetos.modules.platformadmin.domain.TrialSignupRequestRepository;
import com.vetos.modules.platformadmin.domain.TrialSmsPort;
import com.vetos.modules.platformadmin.domain.exception.TrialSignupRequestNotFoundException;
import com.vetos.modules.platformadmin.domain.exception.TrialSignupTooManyRequestsException;
import com.vetos.platform.subscription.TrialProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Dogrulama kodunu yeniden gonderir: bekleme suresi ve istek basina SMS siniri burada uygulanir. */
@Service
public class ResendTrialCodeUseCase {

    private final TrialSignupRequestRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TrialSmsPort trialSmsPort;
    private final TrialOtpGenerator otpGenerator;
    private final TrialProperties properties;
    private final Clock clock;

    @Autowired
    public ResendTrialCodeUseCase(
        TrialSignupRequestRepository repository, PasswordEncoder passwordEncoder, TrialSmsPort trialSmsPort,
        TrialOtpGenerator otpGenerator, TrialProperties properties
    ) {
        this(repository, passwordEncoder, trialSmsPort, otpGenerator, properties, Clock.systemUTC());
    }

    ResendTrialCodeUseCase(
        TrialSignupRequestRepository repository, PasswordEncoder passwordEncoder, TrialSmsPort trialSmsPort,
        TrialOtpGenerator otpGenerator, TrialProperties properties, Clock clock
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.trialSmsPort = trialSmsPort;
        this.otpGenerator = otpGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return bir sonraki yeniden gonderime kadar beklenecek saniye */
    @Transactional
    public int execute(UUID requestId) {
        TrialSignupRequest request = repository.findByIdForUpdate(requestId)
            .filter(r -> !r.isCompleted())
            .orElseThrow(TrialSignupRequestNotFoundException::new);
        Instant now = clock.instant();
        // Yanlis kod hakki bittiyse yeni kod gondermek sayaci asmanin yolu olmamali.
        if (!request.hasAttemptsLeft(properties.otpMaxAttempts())) {
            throw new TrialSignupTooManyRequestsException();
        }
        if (!request.canResend(now, properties.otpResendCooldownSeconds(), properties.otpMaxSends())) {
            throw new TrialSignupTooManyRequestsException();
        }

        String code = otpGenerator.next();
        request.issueCode(passwordEncoder.encode(code), now, properties.otpTtlSeconds());
        repository.save(request);

        trialSmsPort.sendVerificationCode(request.getPhone(), code);
        return properties.otpResendCooldownSeconds();
    }
}
