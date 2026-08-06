package com.vetos.modules.notification.application;

import com.vetos.modules.notification.application.dto.NotificationStatusSummary;
import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetNotificationStatusSummaryUseCase {

    private final NotificationLogRepository notificationLogRepository;

    @Transactional(readOnly = true)
    public NotificationStatusSummary execute(UUID tenantId) {
        List<NotificationLog> logs = notificationLogRepository.findByTenantId(tenantId);

        long pending = logs.stream().filter(l -> l.getStatus() == NotificationStatus.PENDING).count();
        long sent = logs.stream().filter(l -> l.getStatus() == NotificationStatus.SENT).count();
        long failed = logs.stream().filter(l -> l.getStatus() == NotificationStatus.FAILED).count();
        Instant lastSentAt = logs.stream()
            .filter(l -> l.getStatus() == NotificationStatus.SENT)
            .map(NotificationLog::getAttemptedAt)
            .max(Instant::compareTo)
            .orElse(null);

        // Gercek bir SMS/WhatsApp saglayici hesabi/API anahtari bu ortamda
        // mevcut degil -- MockNotificationAdapter aktif oldugu surece false.
        return new NotificationStatusSummary(pending, sent, failed, lastSentAt, false);
    }
}
