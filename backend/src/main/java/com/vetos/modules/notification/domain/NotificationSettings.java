package com.vetos.modules.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Kiraci basina bildirim tercihleri. Su an tek bir ayar tutuyor: randevu onayi
 * ve randevu hatirlatmasi mesajlarinin hangi kanaldan (SMS/WhatsApp) gonderilecegi
 * -- bkz. SendAppointmentRemindersUseCase, AppointmentScheduledEventListener.
 * Bir kiraci icin satir yoksa (henuz hic ayarlamadiysa) SMS varsayilan kabul
 * edilir, bkz. GetNotificationSettingsUseCase.
 */
@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSettings {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_channel", nullable = false)
    private NotificationChannel appointmentChannel;

    public static NotificationSettings defaultFor(UUID tenantId) {
        NotificationSettings settings = new NotificationSettings();
        settings.tenantId = tenantId;
        settings.appointmentChannel = NotificationChannel.SMS;
        return settings;
    }

    public void updateAppointmentChannel(NotificationChannel channel) {
        this.appointmentChannel = channel;
    }
}
