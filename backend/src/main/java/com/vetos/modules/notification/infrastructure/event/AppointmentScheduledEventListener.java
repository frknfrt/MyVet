package com.vetos.modules.notification.infrastructure.event;

import com.vetos.modules.appointment.domain.event.AppointmentScheduledEvent;
import com.vetos.modules.notification.application.QueueNotificationUseCase;
import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationSettingsRepository;
import com.vetos.modules.notification.domain.NotificationType;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.OwnerSummary;
import com.vetos.modules.patient.domain.PatientLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class AppointmentScheduledEventListener {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Istanbul"));

    private final QueueNotificationUseCase queueNotificationUseCase;
    private final OwnerLookupPort ownerLookupPort;
    private final PatientLookupPort patientLookupPort;
    private final NotificationSettingsRepository notificationSettingsRepository;

    @EventListener
    void onAppointmentScheduled(AppointmentScheduledEvent event) {
        var owner = ownerLookupPort.findSummaryById(event.ownerId());
        if (owner.phone() == null || owner.phone().isBlank()) {
            return;
        }
        var patient = patientLookupPort.findSummaryById(event.patientId());

        String message = "Sayin %s, %s icin %s tarihli randevunuz olusturuldu.".formatted(
            owner.fullName(), patient.name(), FORMAT.format(event.scheduledStart())
        );

        NotificationChannel channel = channelFor(resolvePreferredChannel(event.tenantId()), owner);

        queueNotificationUseCase.execute(
            event.tenantId(), event.ownerId(), event.patientId(), channel,
            NotificationType.APPOINTMENT_CONFIRMATION, owner.phone(), message, event.appointmentId(), null
        );
    }

    private NotificationChannel resolvePreferredChannel(UUID tenantId) {
        return notificationSettingsRepository.findByTenantId(tenantId)
            .map(s -> s.getAppointmentChannel())
            .orElse(NotificationChannel.SMS);
    }

    // Klinik WhatsApp'i tercih etse bile, sahip WhatsApp'a acikca izin vermediyse
    // (owner.whatsappConsent() == false) SMS'e dusuyoruz -- ayni kural
    // SendAppointmentRemindersUseCase / SendCampaignUseCase ile tutarli.
    private static NotificationChannel channelFor(NotificationChannel preferredChannel, OwnerSummary owner) {
        if (preferredChannel == NotificationChannel.WHATSAPP && owner.whatsappConsent()) {
            return NotificationChannel.WHATSAPP;
        }
        return NotificationChannel.SMS;
    }
}
