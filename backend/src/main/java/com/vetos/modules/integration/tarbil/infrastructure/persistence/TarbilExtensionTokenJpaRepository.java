package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TarbilExtensionTokenJpaRepository extends JpaRepository<TarbilExtensionToken, UUID> {
    Optional<TarbilExtensionToken> findByPairingCodeHash(String hash);
    Optional<TarbilExtensionToken> findByTokenHash(String hash);
    List<TarbilExtensionToken> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
