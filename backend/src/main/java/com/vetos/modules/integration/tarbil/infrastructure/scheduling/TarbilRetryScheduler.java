package com.vetos.modules.integration.tarbil.infrastructure.scheduling;

import com.vetos.modules.integration.tarbil.application.RetryDueTarbilSyncsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class TarbilRetryScheduler {
    private final RetryDueTarbilSyncsUseCase retryDueTarbilSyncsUseCase;

    @Scheduled(fixedDelay = 120_000) // 2 dakika
    public void retryDue() {
        int retried = retryDueTarbilSyncsUseCase.execute();
        if (retried > 0) {
            log.info("TARBIL otomatik retry kuyruklandi: adet={}", retried);
        }
    }
}
