package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReconcileStuckEInvoiceSubmissionsUseCase {
    private static final Duration STALE_THRESHOLD = Duration.ofHours(1);

    private final EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    private final ApplyEInvoiceCallbackUseCase applyEInvoiceCallbackUseCase;

    @Transactional
    public int execute() {
        List<String> stale = eInvoiceSubmissionRepository.findProviderReferencesByStatusAndAttemptedAtBefore(
            EInvoiceSubmissionStatus.PROCESSING, Instant.now().minus(STALE_THRESHOLD)
        );
        stale.forEach(applyEInvoiceCallbackUseCase::execute);
        return stale.size();
    }
}
