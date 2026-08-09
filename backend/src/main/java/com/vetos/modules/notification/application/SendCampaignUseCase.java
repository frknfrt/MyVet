package com.vetos.modules.notification.application;

import com.vetos.modules.notification.application.dto.CampaignRecipient;
import com.vetos.modules.notification.application.dto.CampaignSendSummary;
import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @docs/requirements.md 4.10 "Kampanya/sadakat programi" -- toplu SMS/WhatsApp
 * kampanyasi. Alici listesi ve degisken degerleri cagiran taraf (frontend, ilgili
 * modulun aday-alici uc noktalarindan topluyor) tarafindan cozulur; bu use-case
 * sadece {token} degistirmesini yapip QueueNotificationUseCase'i her alici icin
 * bir kez cagirir -- yeni bir gonderim mekanizmasi icat edilmez.
 */
@Service
@RequiredArgsConstructor
public class SendCampaignUseCase {

    private final QueueNotificationUseCase queueNotificationUseCase;

    @Transactional
    public CampaignSendSummary execute(UUID tenantId, NotificationChannel channel, String messageTemplate, List<CampaignRecipient> recipients) {
        int queued = 0;
        int skippedNoPhone = 0;
        int skippedNoConsent = 0;

        for (CampaignRecipient recipient : recipients) {
            if (recipient.phone() == null || recipient.phone().isBlank()) {
                skippedNoPhone++;
                continue;
            }
            boolean consented = channel == NotificationChannel.SMS ? recipient.smsConsent() : recipient.whatsappConsent();
            if (!consented) {
                skippedNoConsent++;
                continue;
            }
            String message = substitute(messageTemplate, recipient.variables());
            queueNotificationUseCase.execute(
                tenantId, recipient.ownerId(), null, channel, NotificationType.CAMPAIGN_MESSAGE,
                recipient.phone(), message, null, recipient.label()
            );
            queued++;
        }

        return new CampaignSendSummary(queued, skippedNoPhone, skippedNoConsent);
    }

    private String substitute(String template, Map<String, String> variables) {
        if (variables == null) return template;
        String result = template;
        for (var entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
