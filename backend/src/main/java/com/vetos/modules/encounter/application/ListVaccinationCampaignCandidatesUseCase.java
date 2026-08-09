package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.VaccinationCampaignCandidate;
import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.PatientLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Toplu SMS/WhatsApp kampanyasi icin "Asi Takvimi" alici kaynagi -- bir sahibin
 * araliktaki birden fazla hayvaninin asisi olabilir, sahibe gore dedupe edilip
 * en yakin tarih tutulur.
 */
@Service
@RequiredArgsConstructor
public class ListVaccinationCampaignCandidatesUseCase {

    private final VaccinationRecordRepository vaccinationRecordRepository;
    private final PatientLookupPort patientLookupPort;
    private final OwnerLookupPort ownerLookupPort;

    @Transactional(readOnly = true)
    public List<VaccinationCampaignCandidate> execute(UUID tenantId, LocalDate dueFrom, LocalDate dueTo) {
        Map<UUID, VaccinationCampaignCandidate> byOwner = new LinkedHashMap<>();
        for (VaccinationRecord record : vaccinationRecordRepository.findByTenantId(tenantId)) {
            LocalDate dueDate = record.getNextDueDate();
            if (dueDate == null) continue;
            if (dueFrom != null && dueDate.isBefore(dueFrom)) continue;
            if (dueTo != null && dueDate.isAfter(dueTo)) continue;

            var patient = patientLookupPort.findSummaryById(record.getPatientId());
            var existing = byOwner.get(patient.ownerId());
            if (existing == null || dueDate.isBefore(existing.nextDueDate())) {
                var owner = ownerLookupPort.findSummaryById(patient.ownerId());
                byOwner.put(patient.ownerId(), new VaccinationCampaignCandidate(
                    patient.ownerId(), owner.fullName(), owner.phone(), owner.smsConsent(), owner.whatsappConsent(),
                    patient.name(), record.getVaccineName(), dueDate
                ));
            }
        }
        return List.copyOf(byOwner.values());
    }
}
