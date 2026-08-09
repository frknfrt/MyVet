package com.vetos.modules.appointment.application;

import com.vetos.modules.appointment.application.dto.AppointmentCampaignCandidate;
import com.vetos.modules.appointment.domain.Appointment;
import com.vetos.modules.appointment.domain.AppointmentRepository;
import com.vetos.modules.appointment.domain.AppointmentStatus;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.PatientLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Toplu SMS/WhatsApp kampanyasi icin "Randevular" alici kaynagi -- ayni sahibin
 * araliktaki birden fazla randevusu tek satira dedupe edilir.
 */
@Service
@RequiredArgsConstructor
public class ListAppointmentCampaignCandidatesUseCase {

    private final AppointmentRepository appointmentRepository;
    private final OwnerLookupPort ownerLookupPort;
    private final PatientLookupPort patientLookupPort;

    @Transactional(readOnly = true)
    public List<AppointmentCampaignCandidate> execute(UUID tenantId, Instant from, Instant to, AppointmentStatus status) {
        Map<UUID, AppointmentCampaignCandidate> byOwner = new LinkedHashMap<>();
        for (Appointment appointment : appointmentRepository.findByTenantIdAndDateRange(tenantId, from, to)) {
            if (status != null && appointment.getStatus() != status) continue;
            byOwner.computeIfAbsent(appointment.getOwnerId(), ownerId -> {
                var owner = ownerLookupPort.findSummaryById(ownerId);
                var patient = patientLookupPort.findSummaryById(appointment.getPatientId());
                return new AppointmentCampaignCandidate(
                    ownerId, owner.fullName(), owner.phone(), owner.smsConsent(), owner.whatsappConsent(),
                    patient.name(), appointment.getScheduledStart()
                );
            });
        }
        return List.copyOf(byOwner.values());
    }
}
