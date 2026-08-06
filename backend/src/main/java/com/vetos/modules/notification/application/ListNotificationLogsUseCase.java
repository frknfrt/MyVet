package com.vetos.modules.notification.application;

import com.vetos.modules.notification.application.dto.NotificationLogSummary;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListNotificationLogsUseCase {

    private final NotificationLogRepository notificationLogRepository;
    private final OwnerLookupPort ownerLookupPort;

    @Transactional(readOnly = true)
    public List<NotificationLogSummary> execute(UUID tenantId) {
        return notificationLogRepository.findByTenantId(tenantId).stream()
            .map(log -> new NotificationLogSummary(
                log.getId(), ownerLookupPort.findSummaryById(log.getOwnerId()).fullName(), log.getRecipientContact(),
                log.getChannel(), log.getNotificationType(), log.getMessage(), log.getStatus(), log.getAttemptedAt()
            ))
            .sorted(Comparator.comparing(NotificationLogSummary::attemptedAt).reversed())
            .toList();
    }
}
