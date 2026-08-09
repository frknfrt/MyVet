package com.vetos.modules.notification.infrastructure.event;

import com.vetos.modules.appointment.domain.event.AppointmentScheduledEvent;
import com.vetos.modules.notification.application.QueueNotificationUseCase;
import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationType;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.PatientLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
class AppointmentScheduledEventListener {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Istanbul"));

    private final QueueNotificationUseCase queueNotificationUseCase;
    private final OwnerLookupPort ownerLookupPort;
    private final PatientLookupPort patientLookupPort;

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

        queueNotificationUseCase.execute(
            event.tenantId(), event.ownerId(), event.patientId(), NotificationChannel.SMS,
            NotificationType.APPOINTMENT_CONFIRMATION, owner.phone(), message, event.appointmentId(), null
        );
    }
}
