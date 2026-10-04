package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilStockSnapshotRepositoryAdapter implements TarbilStockSnapshotRepository {

    private final TarbilStockSnapshotJpaRepository snapshots;
    private final TarbilStockSnapshotLineJpaRepository lines;

    @Override public TarbilStockSnapshot save(TarbilStockSnapshot snapshot) { return snapshots.save(snapshot); }

    @Override public void saveLines(List<TarbilStockSnapshotLine> rows) { lines.saveAll(rows); }

    @Override public TarbilStockSnapshotLine saveLine(TarbilStockSnapshotLine line) { return lines.save(line); }

    @Override public Optional<TarbilStockSnapshot> findById(UUID id) { return snapshots.findById(id); }

    @Override public Optional<TarbilStockSnapshot> findByIdForUpdate(UUID id) { return snapshots.findLockedById(id); }

    @Override
    public Optional<TarbilStockSnapshot> findLatest(UUID tenantId, TarbilStockSystem system) {
        return snapshots.findFirstByTenantIdAndTarbilSystemOrderByTakenAtDesc(tenantId, system);
    }

    @Override public List<TarbilStockSnapshotLine> findLines(UUID snapshotId) { return lines.findBySnapshotIdOrderByLineNoAsc(snapshotId); }
}
