package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.application.dto.EInvoiceStatusSummary;
import com.vetos.modules.integration.efatura.domain.EInvoiceGatewayPort;
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
    private final EInvoiceGatewayPort eInvoiceGatewayPort;

    @Transactional(readOnly = true)
    public EInvoiceStatusSummary execute(UUID tenantId) {
        List<EInvoiceSubmission> submissions = eInvoiceSubmissionRepository.findByTenantId(tenantId);

        // PROCESSING (saglayiciya iletildi, GIB resmilesmesi bekleniyor) henuz
        // sonuclanmamis sayilir -- ekranda "Bekliyor" sayacina dahil edilir,
        // frontend'in EInvoiceSubmissionStatus union'ini genisletmeye gerek kalmaz.
        long pending = submissions.stream()
            .filter(s -> s.getStatus() == EInvoiceSubmissionStatus.PENDING || s.getStatus() == EInvoiceSubmissionStatus.PROCESSING)
            .count();
        long submitted = submissions.stream().filter(s -> s.getStatus() == EInvoiceSubmissionStatus.SUBMITTED).count();
        long failed = submissions.stream().filter(s -> s.getStatus() == EInvoiceSubmissionStatus.FAILED).count();
        Instant lastSubmittedAt = submissions.stream()
            .filter(s -> s.getStatus() == EInvoiceSubmissionStatus.SUBMITTED)
            .map(EInvoiceSubmission::getAttemptedAt)
            .max(Instant::compareTo)
            .orElse(null);

        return new EInvoiceStatusSummary(pending, submitted, failed, lastSubmittedAt, eInvoiceGatewayPort.isConfigured());
    }
}
