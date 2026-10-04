package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface TarbilStockSnapshotJpaRepository extends JpaRepository<TarbilStockSnapshot, UUID> {
    Optional<TarbilStockSnapshot> findFirstByTenantIdAndTarbilSystemOrderByTakenAtDesc(UUID tenantId, TarbilStockSystem system);
}
