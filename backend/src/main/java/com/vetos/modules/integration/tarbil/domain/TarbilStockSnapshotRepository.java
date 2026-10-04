package com.vetos.modules.integration.tarbil.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TarbilStockSnapshotRepository {
    TarbilStockSnapshot save(TarbilStockSnapshot snapshot);
    void saveLines(List<TarbilStockSnapshotLine> lines);
    TarbilStockSnapshotLine saveLine(TarbilStockSnapshotLine line);
    Optional<TarbilStockSnapshot> findById(UUID id);
    /** Satir kilidiyle (SELECT ... FOR UPDATE): ayni goruntunun esz zamanli islenmesini siralar. */
    Optional<TarbilStockSnapshot> findByIdForUpdate(UUID id);
    Optional<TarbilStockSnapshot> findLatest(UUID tenantId, TarbilStockSystem system);
    List<TarbilStockSnapshotLine> findLines(UUID snapshotId);
}
