package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.ExtensionSecrets;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.integration.tarbil.domain.exception.InvalidPairingCodeUnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PairExtensionUseCase {

    private static final int MAX_LABEL_LENGTH = 60;

    private final TarbilExtensionTokenRepository repository;

    /** @return ham anahtar -- yalnizca bu yanitta bir kez dondurulur, veritabaninda sadece ozeti durur. */
    @Transactional
    public String execute(String rawCode, String label) {
        String codeHash = ExtensionSecrets.sha256Hex(ExtensionSecrets.normalizePairingCode(rawCode));
        TarbilExtensionToken token = repository.findByPairingCodeHash(codeHash)
            .orElseThrow(InvalidPairingCodeUnauthorizedException::new);
        String rawToken = ExtensionSecrets.newToken();
        String safeLabel = label == null || label.isBlank() ? "Eklenti" : label.trim();
        if (safeLabel.length() > MAX_LABEL_LENGTH) {
            safeLabel = safeLabel.substring(0, MAX_LABEL_LENGTH);
        }
        token.pair(ExtensionSecrets.sha256Hex(rawToken), safeLabel, Instant.now());
        repository.save(token);
        return rawToken;
    }
}
