package com.vetos.modules.dataimport.infrastructure.persistence;

import com.vetos.modules.dataimport.domain.ImportEntityType;
import com.vetos.modules.dataimport.domain.ImportRecordLink;
import com.vetos.modules.dataimport.domain.ImportRecordLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class ImportRecordLinkRepositoryAdapter implements ImportRecordLinkRepository {

    private static final int TARGET_CHUNK_SIZE = 1000;

    private final ImportRecordLinkJpaRepository jpaRepository;

    @Override
    public List<ImportRecordLink> saveAll(List<ImportRecordLink> links) { return jpaRepository.saveAll(links); }

    @Override
    public List<ImportRecordLink> findByBatchId(UUID batchId) { return jpaRepository.findByBatchId(batchId); }

    @Override
    public Set<String> existingSourceIds(UUID tenantId, String sourceSystem, ImportEntityType type,
                                         Collection<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jpaRepository.existingSourceIds(tenantId, sourceSystem, type, sourceIds));
    }

    @Override
    public Optional<UUID> findTarget(UUID tenantId, String sourceSystem, ImportEntityType type, String sourceId) {
        return jpaRepository.findTarget(tenantId, sourceSystem, type, sourceId);
    }

    @Override
    public Optional<ImportRecordLink> findByTarget(ImportEntityType type, UUID targetId) {
        return jpaRepository.findFirstByEntityTypeAndTargetId(type, targetId);
    }

    @Override
    public void deleteByBatchId(UUID batchId) { jpaRepository.deleteByBatchId(batchId); }

    @Override
    public Set<UUID> batchIdsWithLinks(UUID tenantId, Collection<UUID> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jpaRepository.batchIdsWithLinks(tenantId, batchIds));
    }

    @Override
    public List<ImportRecordLink> findLinksOfOtherBatchesToTargets(UUID batchId, Collection<UUID> targetIds, int limit) {
        List<ImportRecordLink> result = new ArrayList<>();
        List<UUID> all = new ArrayList<>(targetIds == null ? List.of() : targetIds);
        for (int from = 0; from < all.size() && result.size() < limit; from += TARGET_CHUNK_SIZE) {
            List<UUID> chunk = all.subList(from, Math.min(all.size(), from + TARGET_CHUNK_SIZE));
            result.addAll(jpaRepository.findLinksOfOtherBatchesToTargets(batchId, chunk,
                PageRequest.of(0, limit - result.size())));
        }
        return result;
    }
}
