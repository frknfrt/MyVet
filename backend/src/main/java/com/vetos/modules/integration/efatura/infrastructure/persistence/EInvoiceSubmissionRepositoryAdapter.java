package com.vetos.modules.integration.efatura.infrastructure.persistence;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class EInvoiceSubmissionRepositoryAdapter implements EInvoiceSubmissionRepository {

    private final EInvoiceSubmissionJpaRepository jpaRepository;

    @Override
    public EInvoiceSubmission save(EInvoiceSubmission submission) { return jpaRepository.save(submission); }

    @Override
    public Optional<EInvoiceSubmission> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<EInvoiceSubmission> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }

    @Override
    public Optional<EInvoiceSubmission> findByProviderReference(String providerReference) {
        return jpaRepository.findByProviderReference(providerReference);
    }

    @Override
    public List<EInvoiceSubmission> claimDueForRetry(Instant now, int limit) {
        return jpaRepository.claimDueForRetry(now, limit);
    }

    @Override
    public List<String> findProviderReferencesByStatusAndAttemptedAtBefore(EInvoiceSubmissionStatus status, Instant threshold) {
        return jpaRepository.findProviderReferencesByStatusAndAttemptedAtBefore(status, threshold);
    }
}
