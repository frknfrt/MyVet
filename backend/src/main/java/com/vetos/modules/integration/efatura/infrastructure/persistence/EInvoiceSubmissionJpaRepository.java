package com.vetos.modules.integration.efatura.infrastructure.persistence;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface EInvoiceSubmissionJpaRepository extends JpaRepository<EInvoiceSubmission, UUID> {
    List<EInvoiceSubmission> findByTenantId(UUID tenantId);
    Optional<EInvoiceSubmission> findByProviderReference(String providerReference);

    // status = 'FAILED' filtresi KESIN -- PROCESSING asla claim edilmez (mukerrer GIB gonderimi riski).
    @Query(value = """
        SELECT * FROM efatura_submission
        WHERE status = 'FAILED' AND next_retry_at IS NOT NULL AND next_retry_at <= :now
        ORDER BY next_retry_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<EInvoiceSubmission> claimDueForRetry(@Param("now") Instant now, @Param("limit") int limit);

    @Query("SELECT s.providerReference FROM EInvoiceSubmission s WHERE s.status = :status AND s.attemptedAt < :threshold")
    List<String> findProviderReferencesByStatusAndAttemptedAtBefore(
        @Param("status") com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus status,
        @Param("threshold") Instant threshold
    );
}
