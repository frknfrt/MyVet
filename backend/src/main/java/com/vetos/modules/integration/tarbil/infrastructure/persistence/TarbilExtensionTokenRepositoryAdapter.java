package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilExtensionTokenRepositoryAdapter implements TarbilExtensionTokenRepository {

    private final TarbilExtensionTokenJpaRepository jpaRepository;

    @Override public TarbilExtensionToken save(TarbilExtensionToken t) { return jpaRepository.save(t); }
    @Override public Optional<TarbilExtensionToken> findById(UUID id) { return jpaRepository.findById(id); }
    @Override public Optional<TarbilExtensionToken> findByPairingCodeHash(String h) { return jpaRepository.findByPairingCodeHash(h); }
    @Override public Optional<TarbilExtensionToken> findByTokenHash(String h) { return jpaRepository.findByTokenHash(h); }

    @Override
    public List<TarbilExtensionToken> findByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }
}
