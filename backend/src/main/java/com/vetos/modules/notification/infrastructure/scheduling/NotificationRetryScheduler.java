package com.vetos.modules.notification.infrastructure.scheduling;

import com.vetos.modules.notification.application.RetryDueNotificationsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class NotificationRetryScheduler {
    private final RetryDueNotificationsUseCase retryDueNotificationsUseCase;

    @Scheduled(fixedDelay = 120_000) // 2 dakika
    public void retryDue() {
        int retried = retryDueNotificationsUseCase.execute();
        if (retried > 0) {
            log.info("Otomatik retry kuyruklandi: adet={}", retried);
        }
    }
}
