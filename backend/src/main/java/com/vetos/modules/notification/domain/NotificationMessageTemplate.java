package com.vetos.modules.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Kiraci + bildirim tipi basina ozellestirilmis mesaj metni (bkz.
 * NotificationMessageTemplateRepository, GetNotificationTemplatesUseCase,
 * UpdateNotificationTemplateUseCase, NotificationTemplateDefaults). Sadece
 * NotificationTemplateDefaults.CUSTOMIZABLE_TYPES icindeki tipler icin satir
 * olusur -- CAMPAIGN_MESSAGE haric, o her gonderimde elle yazilir (bkz.
 * SendCampaignUseCase). Satir yoksa varsayilan metin kullanilir.
 */
@Entity
@Table(
    name = "notification_message_templates",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "notification_type"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationMessageTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Column(name = "template_text", nullable = false, columnDefinition = "text")
    private String templateText;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static NotificationMessageTemplate create(UUID tenantId, NotificationType notificationType, String templateText) {
        NotificationMessageTemplate template = new NotificationMessageTemplate();
        template.tenantId = tenantId;
        template.notificationType = notificationType;
        template.templateText = templateText;
        template.updatedAt = Instant.now();
        return template;
    }

    public void updateText(String templateText) {
        this.templateText = templateText;
        this.updatedAt = Instant.now();
    }
}
