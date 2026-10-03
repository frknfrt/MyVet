package com.vetos.modules.notification.infrastructure.adapter;

import com.vetos.modules.notification.domain.FailedNotificationView;
import com.vetos.modules.notification.domain.NotificationHealthPort;
import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationStatus;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
class NotificationHealthAdapter implements NotificationHealthPort {

    private final NotificationLogRepository notificationLogRepository;
    private final TenantLookupPort tenantLookupPort;

    @Override
    @Transactional(readOnly = true)
    public List<FailedNotificationView> findRecentFailed(int limit) {
        return notificationLogRepository.findRecentByStatus(NotificationStatus.FAILED, limit).stream()
            .map(this::toView)
            .toList();
    }

    private FailedNotificationView toView(NotificationLog log) {
        String tenantName = tenantLookupPort.findTenantName(log.getTenantId()).orElse("Bilinmeyen Klinik");
        return new FailedNotificationView(
            log.getTenantId(), tenantName, log.getRecipientLabel(), log.getRecipientContact(),
            log.getChannel(), log.getNotificationType(), log.getFailureReason(),
            log.getAttemptCount(), log.getAttemptedAt(), log.getNextRetryAt()
        );
    }
}
