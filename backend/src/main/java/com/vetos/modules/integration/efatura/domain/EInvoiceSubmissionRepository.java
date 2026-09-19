package com.vetos.modules.integration.efatura.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EInvoiceSubmissionRepository {
    EInvoiceSubmission save(EInvoiceSubmission submission);
    Optional<EInvoiceSubmission> findById(UUID id);
    List<EInvoiceSubmission> findByTenantId(UUID tenantId);
    Optional<EInvoiceSubmission> findByProviderReference(String providerReference);
    List<EInvoiceSubmission> claimDueForRetry(Instant now, int limit);
}
