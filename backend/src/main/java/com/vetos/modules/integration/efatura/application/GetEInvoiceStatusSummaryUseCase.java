package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.application.dto.EInvoiceStatusSummary;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetEInvoiceStatusSummaryUseCase {

    private final EInvoiceSubmissionRepository eInvoiceSubmissionRepository;

    @Transactional(readOnly = true)
    public EInvoiceStatusSummary execute(UUID tenantId) {
        List<EInvoiceSubmission> submissions = eInvoiceSubmissionRepository.findByTenantId(tenantId);

        long pending = submissions.stream().filter(s -> s.getStatus() == EInvoiceSubmissionStatus.PENDING).count();
        long submitted = submissions.stream().filter(s -> s.getStatus() == EInvoiceSubmissionStatus.SUBMITTED).count();
        long failed = submissions.stream().filter(s -> s.getStatus() == EInvoiceSubmissionStatus.FAILED).count();
        Instant lastSubmittedAt = submissions.stream()
            .filter(s -> s.getStatus() == EInvoiceSubmissionStatus.SUBMITTED)
            .map(EInvoiceSubmission::getAttemptedAt)
            .max(Instant::compareTo)
            .orElse(null);

        // Gercek bir e-Fatura/e-Arsiv saglayici hesabi/API anahtari bu
        // ortamda mevcut degil -- MockEInvoiceGatewayAdapter aktif oldugu
        // surece false.
        return new EInvoiceStatusSummary(pending, submitted, failed, lastSubmittedAt, false);
    }
}
