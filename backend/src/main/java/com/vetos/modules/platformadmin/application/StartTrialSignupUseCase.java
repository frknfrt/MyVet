package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PhoneNumbers;
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
import com.vetos.platform.subscription.TrialProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Self-servis deneme kaydinin ilk adimi: istegi kaydeder ve telefona dogrulama kodu gonderir.
 * Klinik bu adimda OLUSMAZ (bkz. VerifyTrialSignupUseCase).
 */
@Service
public class StartTrialSignupUseCase {

    private final TrialSignupRequestRepository repository;
    private final TenantAdminPort tenantAdminPort;
    private final PasswordEncoder passwordEncoder;
    private final TrialSmsPort trialSmsPort;
    private final TrialOtpGenerator otpGenerator;
    private final TrialProperties properties;
    private final Clock clock;

    @Autowired
    public StartTrialSignupUseCase(
        TrialSignupRequestRepository repository, TenantAdminPort tenantAdminPort, PasswordEncoder passwordEncoder,
        TrialSmsPort trialSmsPort, TrialOtpGenerator otpGenerator, TrialProperties properties
    ) {
        this(repository, tenantAdminPort, passwordEncoder, trialSmsPort, otpGenerator, properties, Clock.systemUTC());
    }

    StartTrialSignupUseCase(
        TrialSignupRequestRepository repository, TenantAdminPort tenantAdminPort, PasswordEncoder passwordEncoder,
        TrialSmsPort trialSmsPort, TrialOtpGenerator otpGenerator, TrialProperties properties, Clock clock
    ) {
        this.repository = repository;
        this.tenantAdminPort = tenantAdminPort;
        this.passwordEncoder = passwordEncoder;
        this.trialSmsPort = trialSmsPort;
        this.otpGenerator = otpGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public TrialSignupStarted execute(StartTrialSignupCommand command) {
        String phone = normalizePhone(command.phone());
        String email = command.email().trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();

        if (tenantAdminPort.isEmailRegistered(email)) {
            throw new SignupEmailAlreadyRegisteredConflictException(email);
        }
        if (repository.existsByPhoneAndStatus(phone, TrialSignupRequestStatus.COMPLETED)) {
            throw new TrialPhoneAlreadyUsedConflictException();
        }
        long recentRequests = repository.countByPhoneAndCreatedAtAfter(phone, now.minus(Duration.ofHours(24)));
        if (recentRequests >= properties.maxRequestsPerPhonePerDay()) {
            throw new TrialSignupTooManyRequestsException();
        }

        String code = otpGenerator.next();
        TrialSignupRequest request = TrialSignupRequest.create(
            command.fullName().trim(), email, phone, command.clinicName().trim(), command.city().trim(),
            command.chamberRegistrationNo().trim(), passwordEncoder.encode(command.password()), now, command.clientIp()
        );
        request.issueCode(passwordEncoder.encode(code), now, properties.otpTtlSeconds());
        request = repository.save(request);

        trialSmsPort.sendVerificationCode(phone, code);
        return new TrialSignupStarted(request.getId(), properties.otpResendCooldownSeconds());
    }

    static String normalizePhone(String raw) {
        try {
            return PhoneNumbers.toE164Tr(raw);
        } catch (IllegalArgumentException e) {
            throw new TrialPhoneInvalidException();
        }
    }
}
