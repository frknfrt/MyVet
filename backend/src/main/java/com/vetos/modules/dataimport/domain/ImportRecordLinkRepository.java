package com.vetos.modules.dataimport.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ImportRecordLinkRepository {

    List<ImportRecordLink> saveAll(List<ImportRecordLink> links);

    List<ImportRecordLink> findByBatchId(UUID batchId);

    Set<String> existingSourceIds(UUID tenantId, String sourceSystem, ImportEntityType type, Collection<String> sourceIds);

    Optional<UUID> findTarget(UUID tenantId, String sourceSystem, ImportEntityType type, String sourceId);

    Optional<ImportRecordLink> findByTarget(ImportEntityType type, UUID targetId);

    void deleteByBatchId(UUID batchId);

    /** Verilen partilerden en az bir linki olanlar (kiraci kosulu acik; root oturumda da dogru calisir). */
    Set<UUID> batchIdsWithLinks(UUID tenantId, Collection<UUID> batchIds);

    /**
     * Verilen hedeflere (owners/patients kimlikleri) baglanan, {@code batchId} DISINDAKI partilerin linkleri
     * (created_by_import degeri ne olursa olsun); en fazla {@code limit} adet. Kiraci filtresi (@TenantId) uygulanir.
     */
    List<ImportRecordLink> findLinksOfOtherBatchesToTargets(UUID batchId, Collection<UUID> targetIds, int limit);
}
