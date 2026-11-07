package com.vetos.modules.dataimport.infrastructure.persistence;

import com.vetos.modules.dataimport.domain.ImportEntityType;
import com.vetos.modules.dataimport.domain.ImportRecordLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ImportRecordLinkJpaRepository extends JpaRepository<ImportRecordLink, UUID> {

    List<ImportRecordLink> findByBatchId(UUID batchId);

    @Query("SELECT l.sourceId FROM ImportRecordLink l WHERE l.tenantId = :tenantId " +
        "AND l.sourceSystem = :sourceSystem AND l.entityType = :type AND l.sourceId IN :sourceIds")
    List<String> existingSourceIds(@Param("tenantId") UUID tenantId, @Param("sourceSystem") String sourceSystem,
                                   @Param("type") ImportEntityType type, @Param("sourceIds") Collection<String> sourceIds);

    @Query("SELECT l.targetId FROM ImportRecordLink l WHERE l.tenantId = :tenantId " +
        "AND l.sourceSystem = :sourceSystem AND l.entityType = :type AND l.sourceId = :sourceId")
    Optional<UUID> findTarget(@Param("tenantId") UUID tenantId, @Param("sourceSystem") String sourceSystem,
                              @Param("type") ImportEntityType type, @Param("sourceId") String sourceId);

    Optional<ImportRecordLink> findFirstByEntityTypeAndTargetId(ImportEntityType type, UUID targetId);

    @Query("SELECT l FROM ImportRecordLink l WHERE l.batchId <> :batchId AND l.targetId IN :targetIds " +
        "ORDER BY l.createdAt, l.id")
    List<ImportRecordLink> findLinksOfOtherBatchesToTargets(@Param("batchId") UUID batchId,
                                                            @Param("targetIds") Collection<UUID> targetIds,
                                                            org.springframework.data.domain.Pageable page);

    @Query("SELECT DISTINCT l.batchId FROM ImportRecordLink l WHERE l.tenantId = :tenantId AND l.batchId IN :batchIds")
    List<UUID> batchIdsWithLinks(@Param("tenantId") UUID tenantId, @Param("batchIds") Collection<UUID> batchIds);

    @Modifying
    @Query("DELETE FROM ImportRecordLink l WHERE l.batchId = :batchId")
    void deleteByBatchId(@Param("batchId") UUID batchId);
}
