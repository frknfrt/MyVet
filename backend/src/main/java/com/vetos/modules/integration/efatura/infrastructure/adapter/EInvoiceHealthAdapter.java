package com.vetos.modules.integration.efatura.infrastructure.adapter;

import com.vetos.modules.integration.efatura.domain.EInvoiceHealthPort;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import com.vetos.modules.integration.efatura.domain.FailedEInvoiceView;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
class EInvoiceHealthAdapter implements EInvoiceHealthPort {

    private final EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    private final TenantLookupPort tenantLookupPort;

    @Override
    @Transactional(readOnly = true)
    public List<FailedEInvoiceView> findRecentFailed(int limit) {
        return eInvoiceSubmissionRepository.findRecentByStatus(EInvoiceSubmissionStatus.FAILED, limit).stream()
            .map(this::toView)
            .toList();
    }

    private FailedEInvoiceView toView(EInvoiceSubmission submission) {
        String tenantName = tenantLookupPort.findTenantName(submission.getTenantId()).orElse("Bilinmeyen Klinik");
        return new FailedEInvoiceView(
            submission.getId(), submission.getTenantId(), tenantName, submission.getInvoiceId(), submission.getDocumentType(),
            submission.getTotalAmount(), submission.getFailureReason(), submission.getAttemptCount(), submission.getAttemptedAt()
        );
    }
}
