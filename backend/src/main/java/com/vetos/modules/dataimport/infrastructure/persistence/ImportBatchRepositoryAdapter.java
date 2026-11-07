package com.vetos.modules.dataimport.infrastructure.persistence;

import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.ImportBatchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class ImportBatchRepositoryAdapter implements ImportBatchRepository {

    private final ImportBatchJpaRepository jpaRepository;

    @Override
    public ImportBatch save(ImportBatch batch) { return jpaRepository.save(batch); }

    @Override
    public Optional<ImportBatch> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public Optional<ImportBatch> findByIdForUpdate(UUID id) { return jpaRepository.findByIdForUpdate(id); }

    @Override
    public List<ImportBatch> findByTenantIdOrderByCreatedAtDesc(UUID tenantId) {
        return jpaRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Override
    public List<String> findSourceSystems(UUID tenantId) {
        return jpaRepository.findSourceSystems(tenantId);
    }

    @Override
    public List<ImportBatch> findByStatus(ImportBatchStatus status) {
        return jpaRepository.findByStatus(status);
    }

    @Override
    public List<UUID> findIdsByStatusAndCreatedAtBefore(ImportBatchStatus status, Instant before) {
        return jpaRepository.findIdsByStatusAndCreatedAtBefore(status, before);
    }
}
