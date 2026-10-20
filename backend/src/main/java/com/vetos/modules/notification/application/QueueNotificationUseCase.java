package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationType;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueNotificationUseCase {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(QueueNotificationUseCase.class);

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationSendExecutor notificationSendExecutor;
    private final UsageQuotaPort usageQuotaPort;

    @Transactional
    public UUID execute(
        UUID tenantId, UUID ownerId, UUID patientId, NotificationChannel channel, NotificationType notificationType,
        String recipientContact, String message, UUID relatedEntityId, String recipientLabel
    ) {
        NotificationLog log = notificationLogRepository.save(
            NotificationLog.queue(tenantId, ownerId, patientId, channel, notificationType, recipientContact, message, relatedEntityId, recipientLabel)
        );
        if (notificationType != NotificationType.CAMPAIGN_MESSAGE) {
            // Otomatik mesajlar asla engellenmez ama kotaya sayilir (kampanya SendCampaignUseCase'te ayrildi).
            // Sayim hatasi (DB, havuz zaman asimi) cagiranin islemini asla bozmamali: yut ve logla.
            try {
                usageQuotaPort.recordUnconditional(tenantId, UsageKind.MESSAGE, 1);
            } catch (RuntimeException e) {
                logger.warn("Mesaj kotasi sayimi basarisiz, bildirim yine kuyruga alindi: tenantId={}", tenantId, e);
            }
        }
        UUID logId = log.getId();

        // Cagiran kod (appointment event listener'lari, zamanlanmis hatirlatma
        // isi) genelde bir ust @Transactional icinde calisiyor -- gonderim
        // denemesi, bu satir henuz commit olmamis kaydi goremez diye
        // transaction commit'ten SONRAYA erteleniyor (QueueTarbilSyncUseCase
        // ile ayni desen).
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationSendExecutor.attemptSend(logId);
                }
            });
        } else {
            notificationSendExecutor.attemptSend(logId);
        }

        return logId;
    }
}
