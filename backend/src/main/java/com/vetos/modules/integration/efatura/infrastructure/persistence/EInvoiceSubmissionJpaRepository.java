package com.vetos.modules.integration.efatura.infrastructure.persistence;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface EInvoiceSubmissionJpaRepository extends JpaRepository<EInvoiceSubmission, UUID> {
    List<EInvoiceSubmission> findByTenantId(UUID tenantId);
    Optional<EInvoiceSubmission> findByProviderReference(String providerReference);
}
