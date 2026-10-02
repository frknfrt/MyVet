package com.vetos.modules.integration.tarbil.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TarbilExtensionTokenRepository {
    TarbilExtensionToken save(TarbilExtensionToken token);
    Optional<TarbilExtensionToken> findById(UUID id);
    Optional<TarbilExtensionToken> findByPairingCodeHash(String hash);
    Optional<TarbilExtensionToken> findByTokenHash(String hash);
    List<TarbilExtensionToken> findByTenantId(UUID tenantId);
}
