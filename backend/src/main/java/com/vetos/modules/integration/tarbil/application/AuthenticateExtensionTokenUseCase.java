package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.ExtensionIdentity;
import com.vetos.modules.integration.tarbil.domain.ExtensionSecrets;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthenticateExtensionTokenUseCase {

    private final TarbilExtensionTokenRepository repository;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional
    public Optional<ExtensionIdentity> execute(String rawToken) {
        if (rawToken == null || !rawToken.startsWith("vtx_")) {
            return Optional.empty();
        }
        Optional<TarbilExtensionToken> found = repository.findByTokenHash(ExtensionSecrets.sha256Hex(rawToken))
            .filter(TarbilExtensionToken::isUsable)
            .filter(t -> staffUserLookupPort.isActive(t.getStaffUserId()));
        found.ifPresent(t -> {
            t.touch(Instant.now());
            repository.save(t);
        });
        return found.map(t -> new ExtensionIdentity(t.getId(), t.getTenantId(), t.getStaffUserId()));
    }
}
