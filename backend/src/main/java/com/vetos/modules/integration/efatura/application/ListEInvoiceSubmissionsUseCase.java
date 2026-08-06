package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.application.dto.EInvoiceSubmissionSummary;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListEInvoiceSubmissionsUseCase {

    private final EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    private final OwnerLookupPort ownerLookupPort;

    @Transactional(readOnly = true)
    public List<EInvoiceSubmissionSummary> execute(UUID tenantId) {
        return eInvoiceSubmissionRepository.findByTenantId(tenantId).stream()
            .map(s -> new EInvoiceSubmissionSummary(
                s.getId(), s.getInvoiceId(), ownerLookupPort.findSummaryById(s.getOwnerId()).fullName(),
                s.getDocumentType(), s.getStatus(), s.getGibReference(), s.getAttemptedAt()
            ))
            .sorted(Comparator.comparing(EInvoiceSubmissionSummary::attemptedAt).reversed())
            .toList();
    }
}
