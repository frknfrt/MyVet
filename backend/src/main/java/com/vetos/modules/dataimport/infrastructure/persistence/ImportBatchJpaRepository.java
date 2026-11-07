package com.vetos.modules.dataimport.infrastructure.persistence;

import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ImportBatchJpaRepository extends JpaRepository<ImportBatch, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ImportBatch b WHERE b.id = :id")
    Optional<ImportBatch> findByIdForUpdate(@Param("id") UUID id);

    List<ImportBatch> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<ImportBatch> findByStatus(ImportBatchStatus status);

    @Query("SELECT b.sourceSystem FROM ImportBatch b WHERE b.tenantId = :tenantId "
        + "GROUP BY b.sourceSystem ORDER BY MIN(b.createdAt), b.sourceSystem")
    List<String> findSourceSystems(@Param("tenantId") UUID tenantId);

    @Query("SELECT b.id FROM ImportBatch b WHERE b.status = :status AND b.createdAt < :before")
    List<UUID> findIdsByStatusAndCreatedAtBefore(@Param("status") ImportBatchStatus status,
                                                 @Param("before") Instant before);
}
