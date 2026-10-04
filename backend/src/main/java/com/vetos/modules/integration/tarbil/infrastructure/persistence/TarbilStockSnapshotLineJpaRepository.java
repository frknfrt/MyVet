package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface TarbilStockSnapshotLineJpaRepository extends JpaRepository<TarbilStockSnapshotLine, UUID> {
    List<TarbilStockSnapshotLine> findBySnapshotIdOrderByLineNoAsc(UUID snapshotId);
}
