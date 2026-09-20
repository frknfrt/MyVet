package com.vetos.modules.notification.application;

import com.vetos.modules.appointment.domain.AppointmentLookupPort;
import com.vetos.modules.appointment.domain.AppointmentReminderCandidate;
import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationType;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.PatientLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * @docs/requirements.md 4.5 "No-show tahmini (AI) ve otomatik hatirlatma
 * (SMS/WhatsApp/push, cok kanalli)" -- gunluk zamanlanmis is
 * (AppointmentReminderScheduler) tarafindan her aktif kiraci icin cagrilir.
 * Ayni randevu icin birden fazla hatirlatma kuyruklanmasini onlemek adina
 * NotificationLogRepository.existsByRelatedEntityIdAndNotificationType ile
 * idempotentlik saglanir.
 */
@Service
@RequiredArgsConstructor
public class SendAppointmentRemindersUseCase {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Istanbul"));

    private final AppointmentLookupPort appointmentLookupPort;
    private final NotificationLogRepository notificationLogRepository;
    private final QueueNotificationUseCase queueNotificationUseCase;
    private final OwnerLookupPort ownerLookupPort;
    private final PatientLookupPort patientLookupPort;

    // REQUIRES_NEW: bu metot RANDEVU_HATIRLATMA_KILIDI'ni tutan disi transaction'a (AppointmentReminderScheduler)
    // katilirsa, bir kiracinin hatasi TUM kiracilarin isini sessizce geri alir (Spring
    // globalRollbackOnParticipationFailure) -- bagimsiz transaction bunu onler, kilit disi transaction askiya
    // alinip devam ettigi icin etkilenmez.
    // Ikinci, bagimsiz bir sebep: disi transaction'in Hibernate Session'i
    // TenantContext.set(tenantId)'den ONCE aciliyor (kilit tutan scheduler
    // metodu @Transactional'a girdigi anda), yani bu Session filtresiz/root
    // kalir. REQUIRES_NEW her kiraci icin TenantContext.set SONRASI taze bir
    // Session acar -- @TenantId filtresi bu sayede dogru kiraciya gore
    // calisir. REQUIRES_NEW olmasa, bu use case'e ileride @TenantId'li bir
    // entity'ye disi (filtresiz) Session uzerinden dokunan bir kod eklenirse,
    // sessizce tum kiracilar arasinda sorgu yapardi.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int execute(UUID tenantId, Instant rangeStart, Instant rangeEnd) {
        int queued = 0;
        for (AppointmentReminderCandidate candidate : appointmentLookupPort.findConfirmedBetween(tenantId, rangeStart, rangeEnd)) {
            if (notificationLogRepository.existsByRelatedEntityIdAndNotificationType(candidate.appointmentId(), NotificationType.APPOINTMENT_REMINDER)) {
                continue;
            }
            var owner = ownerLookupPort.findSummaryById(candidate.ownerId());
            if (owner.phone() == null || owner.phone().isBlank()) {
                continue;
            }
            var patient = patientLookupPort.findSummaryById(candidate.patientId());

            String message = "Sayin %s, %s icin yarin %s randevunuz var. Bu bir hatirlatma mesajidir.".formatted(
                owner.fullName(), patient.name(), FORMAT.format(candidate.scheduledStart())
            );

            queueNotificationUseCase.execute(
                tenantId, candidate.ownerId(), candidate.patientId(), NotificationChannel.SMS,
                NotificationType.APPOINTMENT_REMINDER, owner.phone(), message, candidate.appointmentId(), null
            );
            queued++;
        }
        return queued;
    }
}
