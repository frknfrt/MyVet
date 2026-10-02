package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.PairingCode;
import com.vetos.modules.integration.tarbil.domain.ExtensionSecrets;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatePairingCodeUseCase {

    static final Duration CODE_TTL = Duration.ofMinutes(10);

    private final TarbilExtensionTokenRepository repository;

    @Transactional
    public PairingCode execute(UUID tenantId, UUID staffId) {
        String code = ExtensionSecrets.newPairingCode();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(CODE_TTL);
        repository.save(TarbilExtensionToken.issuePairing(tenantId, staffId, ExtensionSecrets.sha256Hex(code), expiresAt, now));
        return new PairingCode(code, expiresAt);
    }
}
