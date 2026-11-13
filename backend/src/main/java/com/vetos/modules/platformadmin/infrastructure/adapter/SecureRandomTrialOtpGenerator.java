package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.TrialOtpGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
class SecureRandomTrialOtpGenerator implements TrialOtpGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String next() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
