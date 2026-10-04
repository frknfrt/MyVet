package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface TarbilStockSnapshotJpaRepository extends JpaRepository<TarbilStockSnapshot, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TarbilStockSnapshot s where s.id = :id")
    Optional<TarbilStockSnapshot> findLockedById(@Param("id") UUID id);

    Optional<TarbilStockSnapshot> findFirstByTenantIdAndTarbilSystemOrderByTakenAtDesc(UUID tenantId, TarbilStockSystem system);
}
