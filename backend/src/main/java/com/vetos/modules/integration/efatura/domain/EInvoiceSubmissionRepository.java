package com.vetos.modules.integration.efatura.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EInvoiceSubmissionRepository {
    EInvoiceSubmission save(EInvoiceSubmission submission);
    Optional<EInvoiceSubmission> findById(UUID id);
    List<EInvoiceSubmission> findByTenantId(UUID tenantId);
}
