package com.vetos.modules.integration.efatura.infrastructure.scheduling;

import com.vetos.modules.integration.efatura.application.RetryDueEInvoiceSubmissionsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class EInvoiceRetryScheduler {
    private final RetryDueEInvoiceSubmissionsUseCase retryDueEInvoiceSubmissionsUseCase;

    @Scheduled(fixedDelay = 120_000) // 2 dakika
    public void retryDue() {
        int retried = retryDueEInvoiceSubmissionsUseCase.execute();
        if (retried > 0) {
            log.info("e-Fatura otomatik retry kuyruklandi: adet={}", retried);
        }
    }
}
