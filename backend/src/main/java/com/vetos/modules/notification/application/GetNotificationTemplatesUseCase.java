package com.vetos.modules.notification.application;

import com.vetos.modules.notification.application.dto.NotificationTemplateSummary;
import com.vetos.modules.notification.domain.NotificationMessageTemplateRepository;
import com.vetos.modules.notification.domain.NotificationTemplateDefaults;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Klinigin ozellestirebilecegi otomatik bildirim tiplerini, varsa kendi
 * metniyle, yoksa NotificationTemplateDefaults varsayilaniyla dondurur --
 * bkz. SettingsTab.tsx (sablon bolumu). */
@Service
@RequiredArgsConstructor
public class GetNotificationTemplatesUseCase {

    private final NotificationMessageTemplateRepository notificationMessageTemplateRepository;

    public List<NotificationTemplateSummary> execute(UUID tenantId) {
        Map<com.vetos.modules.notification.domain.NotificationType, String> customized = notificationMessageTemplateRepository
            .findAllByTenantId(tenantId).stream()
            .collect(Collectors.toMap(
                com.vetos.modules.notification.domain.NotificationMessageTemplate::getNotificationType,
                com.vetos.modules.notification.domain.NotificationMessageTemplate::getTemplateText
            ));

        return NotificationTemplateDefaults.CUSTOMIZABLE_TYPES.stream()
            .map(type -> new NotificationTemplateSummary(
                type,
                customized.getOrDefault(type, NotificationTemplateDefaults.defaultTextFor(type)),
                customized.containsKey(type),
                NotificationTemplateDefaults.placeholdersFor(type)
            ))
            .toList();
    }
}
