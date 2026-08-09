package com.vetos.modules.notification.application;

import com.vetos.modules.notification.application.dto.NotificationLogSummary;
import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationStatus;
import com.vetos.modules.notification.domain.NotificationType;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListNotificationLogsUseCase {

    private final NotificationLogRepository notificationLogRepository;
    private final OwnerLookupPort ownerLookupPort;

    @Transactional(readOnly = true)
    public List<NotificationLogSummary> execute(
        UUID tenantId, NotificationChannel channel, NotificationStatus status,
        NotificationType notificationType, Instant from, Instant to, String search
    ) {
        return notificationLogRepository.findByTenantId(tenantId).stream()
            .filter(log -> channel == null || log.getChannel() == channel)
            .filter(log -> status == null || log.getStatus() == status)
            .filter(log -> notificationType == null || log.getNotificationType() == notificationType)
            .filter(log -> from == null || !log.getAttemptedAt().isBefore(from))
            .filter(log -> to == null || log.getAttemptedAt().isBefore(to))
            .filter(log -> search == null || search.isBlank() || log.getMessage().toLowerCase().contains(search.toLowerCase()))
            .map(this::toSummary)
            .sorted(Comparator.comparing(NotificationLogSummary::attemptedAt).reversed())
            .toList();
    }

    private NotificationLogSummary toSummary(NotificationLog log) {
        String displayName = log.getOwnerId() != null
            ? ownerLookupPort.findSummaryById(log.getOwnerId()).fullName()
            : (log.getRecipientLabel() != null ? log.getRecipientLabel() : "Özel Numara");
        return new NotificationLogSummary(
            log.getId(), displayName, log.getRecipientContact(),
            log.getChannel(), log.getNotificationType(), log.getMessage(), log.getStatus(), log.getAttemptedAt()
        );
    }
}
