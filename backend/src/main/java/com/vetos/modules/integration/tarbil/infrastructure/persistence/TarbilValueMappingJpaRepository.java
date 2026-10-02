package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TarbilValueMappingJpaRepository extends JpaRepository<TarbilValueMapping, UUID> {
    Optional<TarbilValueMapping> findByTenantIdAndKindAndVetlyKey(UUID tenantId, TarbilMappingKind kind, String vetlyKey);
    List<TarbilValueMapping> findByTenantIdOrderByKindAscVetlyKeyAsc(UUID tenantId);
}
