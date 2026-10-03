package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationMessageTemplate;
import com.vetos.modules.notification.domain.NotificationMessageTemplateRepository;
import com.vetos.modules.notification.domain.NotificationTemplateDefaults;
import com.vetos.modules.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Bos/whitespace metin gonderilirse klinigin ozellestirmesi silinir ve
 * NotificationTemplateDefaults varsayilanina donulur (bkz. SettingsTab.tsx
 * "Varsayılana Dön" butonu). */
@Service
@RequiredArgsConstructor
public class UpdateNotificationTemplateUseCase {

    private final NotificationMessageTemplateRepository notificationMessageTemplateRepository;

    @Transactional
    public void execute(UUID tenantId, NotificationType notificationType, String templateText) {
        if (!NotificationTemplateDefaults.CUSTOMIZABLE_TYPES.contains(notificationType)) {
            throw new IllegalArgumentException("Bu bildirim tipi icin ozellestirilebilir sablon yok: " + notificationType);
        }
        if (templateText == null || templateText.isBlank()) {
            notificationMessageTemplateRepository.deleteByTenantIdAndNotificationType(tenantId, notificationType);
            return;
        }
        notificationMessageTemplateRepository.findByTenantIdAndNotificationType(tenantId, notificationType)
            .map(existing -> {
                existing.updateText(templateText);
                return existing;
            })
            .or(() -> java.util.Optional.of(NotificationMessageTemplate.create(tenantId, notificationType, templateText)))
            .ifPresent(notificationMessageTemplateRepository::save);
    }
}
