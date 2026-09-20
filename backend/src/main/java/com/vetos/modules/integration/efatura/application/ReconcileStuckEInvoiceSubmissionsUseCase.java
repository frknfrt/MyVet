package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReconcileStuckEInvoiceSubmissionsUseCase {
    private static final Duration STALE_THRESHOLD = Duration.ofHours(1);

    private final EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    private final ApplyEInvoiceCallbackUseCase applyEInvoiceCallbackUseCase;

    public int execute() {
        List<String> stale = eInvoiceSubmissionRepository.findProviderReferencesByStatusAndAttemptedAtBefore(
            EInvoiceSubmissionStatus.PROCESSING, Instant.now().minus(STALE_THRESHOLD)
        );
        for (String providerReference : stale) {
            try {
                applyEInvoiceCallbackUseCase.execute(providerReference);
            } catch (Exception e) {
                log.error("e-Fatura uzlastirmasi basarisiz: providerReference={}", providerReference, e);
            }
        }
        return stale.size();
    }
}
