package com.vetos.modules.notification.application;

import com.vetos.modules.notification.application.dto.CampaignRecipient;
import com.vetos.modules.notification.application.dto.CampaignSendSummary;
import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationType;
import com.vetos.platform.quota.MonthlyQuotaExceededException;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import com.vetos.platform.ratelimit.TenantPool;
import com.vetos.platform.ratelimit.TenantRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    private final TenantRateLimiter tenantRateLimiter;
    private final UsageQuotaPort usageQuotaPort;

    @Transactional
    public CampaignSendSummary execute(UUID tenantId, NotificationChannel channel, String messageTemplate, List<CampaignRecipient> recipients) {
        int skippedNoPhone = 0;
        int skippedNoConsent = 0;
        List<CampaignRecipient> eligible = new ArrayList<>();
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
            eligible.add(recipient);
        }

        int n = eligible.size();
        if (n > 0) {
            tenantRateLimiter.consumeOrThrow(TenantPool.CAMPAIGN_RECIPIENTS, tenantId, n);
            try {
                usageQuotaPort.reserve(tenantId, UsageKind.MESSAGE, n);
            } catch (MonthlyQuotaExceededException e) {
                tenantRateLimiter.refund(TenantPool.CAMPAIGN_RECIPIENTS, tenantId, n);
                throw e;
            }
            try {
                for (CampaignRecipient recipient : eligible) {
                    queueNotificationUseCase.execute(
                        tenantId, recipient.ownerId(), null, channel, NotificationType.CAMPAIGN_MESSAGE,
                        recipient.phone(), substitute(messageTemplate, recipient.variables()), null, recipient.label()
                    );
                }
            } catch (RuntimeException e) {
                // Kota REQUIRES_NEW ile yazildi; bu islem geri alinacagi icin ayrilan hak elle iade edilir.
                usageQuotaPort.release(tenantId, UsageKind.MESSAGE, n);
                throw e;
            }
        }
        return new CampaignSendSummary(n, skippedNoPhone, skippedNoConsent);
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
