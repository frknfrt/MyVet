package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMapping;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilValueMappingRepositoryAdapter implements TarbilValueMappingRepository {

    private final TarbilValueMappingJpaRepository jpaRepository;

    @Override public TarbilValueMapping save(TarbilValueMapping m) { return jpaRepository.save(m); }
    @Override public Optional<TarbilValueMapping> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public Optional<TarbilValueMapping> findByTenantIdAndKindAndVetlyKey(UUID tenantId, TarbilMappingKind kind, String key) {
        return jpaRepository.findByTenantIdAndKindAndVetlyKey(tenantId, kind, key);
    }

    @Override
    public List<TarbilValueMapping> findByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantIdOrderByKindAscVetlyKeyAsc(tenantId);
    }

    @Override public void delete(TarbilValueMapping m) { jpaRepository.delete(m); }
}
