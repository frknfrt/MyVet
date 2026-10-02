package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilExtensionTokenNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RevokeExtensionTokenUseCase {

    private final TarbilExtensionTokenRepository repository;

    @Transactional
    public void execute(UUID tenantId, UUID callerStaffId, boolean callerIsAdmin, UUID tokenId) {
        TarbilExtensionToken token = repository.findById(tokenId)
            .filter(t -> t.getTenantId().equals(tenantId))
            .filter(t -> callerIsAdmin || t.getStaffUserId().equals(callerStaffId))
            .orElseThrow(() -> new TarbilExtensionTokenNotFoundException(tokenId));
        token.revoke(Instant.now());
        repository.save(token);
    }
}
