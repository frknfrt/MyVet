package com.vetos.modules.notification.application;

import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.encounter.domain.VaccinationReminderCandidate;
import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationMessageTemplateRepository;
import com.vetos.modules.notification.domain.NotificationSettingsRepository;
import com.vetos.modules.notification.domain.NotificationTemplateDefaults;
import com.vetos.modules.notification.domain.NotificationType;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.OwnerSummary;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.PatientSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * @docs/requirements.md 4.3 "Turkce ozel asi protokolleri, otomatik
 * hatirlatma (sahibe + hekime), sertifika/aşı karnesi PDF uretimi" -- su an
 * sadece sahibe hatirlatma kismi yapiliyor (hekime bildirim kapsam disi).
 * AppointmentReminderScheduler ile ayni gunluk zamanlanmis-is desenini
 * izler -- VaccinationReminderScheduler tarafindan her aktif kiraci icin
 * cagrilir. Ayni asi kaydina birden fazla hatirlatma kuyruklanmasini
 * onlemek icin NotificationLogRepository.existsByRelatedEntityIdAndNotificationType
 * ile idempotentlik saglanir (VaccinationRecord.reminderSent alani su an
 * kullanilmiyor, ayni amacla iki mekanizma tutmamak icin).
 */
@Service
@RequiredArgsConstructor
public class SendVaccinationRemindersUseCase {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final VaccinationLookupPort vaccinationLookupPort;
    private final NotificationLogRepository notificationLogRepository;
    private final QueueNotificationUseCase queueNotificationUseCase;
    private final OwnerLookupPort ownerLookupPort;
    private final PatientLookupPort patientLookupPort;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final NotificationMessageTemplateRepository notificationMessageTemplateRepository;

    // REQUIRES_NEW: SendAppointmentRemindersUseCase'deki ayni iki sebep burada da
    // gecerli (kilit tutan disi transaction'a katilmama + TenantContext sonrasi
    // taze Session).
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int execute(UUID tenantId, LocalDate dueDate) {
        NotificationChannel preferredChannel = resolvePreferredChannel(tenantId);
        String template = notificationMessageTemplateRepository
            .findByTenantIdAndNotificationType(tenantId, NotificationType.VACCINATION_REMINDER)
            .map(com.vetos.modules.notification.domain.NotificationMessageTemplate::getTemplateText)
            .orElse(NotificationTemplateDefaults.defaultTextFor(NotificationType.VACCINATION_REMINDER));
        int queued = 0;
        for (VaccinationReminderCandidate candidate : vaccinationLookupPort.findDueForReminder(tenantId, dueDate)) {
            if (notificationLogRepository.existsByRelatedEntityIdAndNotificationType(candidate.recordId(), NotificationType.VACCINATION_REMINDER)) {
                continue;
            }
            PatientSummary patient = patientLookupPort.findSummaryById(candidate.patientId());
            OwnerSummary owner = ownerLookupPort.findSummaryById(patient.ownerId());
            if (owner.phone() == null || owner.phone().isBlank()) {
                continue;
            }

            String message = NotificationTemplateDefaults.render(template, Map.of(
                "sahipAdi", owner.fullName(),
                "hastaAdi", patient.name(),
                "asiAdi", candidate.vaccineName(),
                "tarih", FORMAT.format(candidate.nextDueDate())
            ));

            queueNotificationUseCase.execute(
                tenantId, patient.ownerId(), candidate.patientId(), channelFor(preferredChannel, owner),
                NotificationType.VACCINATION_REMINDER, owner.phone(), message, candidate.recordId(), null
            );
            queued++;
        }
        return queued;
    }

    private NotificationChannel resolvePreferredChannel(UUID tenantId) {
        return notificationSettingsRepository.findByTenantId(tenantId)
            .map(s -> s.getAppointmentChannel())
            .orElse(NotificationChannel.SMS);
    }

    private static NotificationChannel channelFor(NotificationChannel preferredChannel, OwnerSummary owner) {
        if (preferredChannel == NotificationChannel.WHATSAPP && owner.whatsappConsent()) {
            return NotificationChannel.WHATSAPP;
        }
        return NotificationChannel.SMS;
    }
}
