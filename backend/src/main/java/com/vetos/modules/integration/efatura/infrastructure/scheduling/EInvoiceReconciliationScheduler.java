package com.vetos.modules.integration.efatura.infrastructure.scheduling;

import com.vetos.modules.integration.efatura.application.ReconcileStuckEInvoiceSubmissionsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class EInvoiceReconciliationScheduler {
    private final ReconcileStuckEInvoiceSubmissionsUseCase reconcileStuckEInvoiceSubmissionsUseCase;

    @Scheduled(fixedDelay = 3_600_000) // saatte bir -- FAILED retry'lerden (2dk) cok daha seyrek,
                                        // aciliyeti yok, saglayici API'sini gereksiz yormasin
    public void reconcile() {
        int resolved = reconcileStuckEInvoiceSubmissionsUseCase.execute();
        if (resolved > 0) {
            log.info("e-Fatura uzlastirmasi tetiklendi: adet={}", resolved);
        }
    }
}
