package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.EncounterReportData;
import com.vetos.modules.encounter.domain.DrugCatalogRepository;
import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.Prescription;
import com.vetos.modules.encounter.domain.PrescriptionItem;
import com.vetos.modules.encounter.domain.PrescriptionItemRepository;
import com.vetos.modules.encounter.domain.PrescriptionRepository;
import com.vetos.modules.encounter.domain.PrescriptionStatus;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.PatientSummary;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tek muayenenin (encounter) hasta sahibine gosterilecek "Muayene Raporu"
 * PDF'i icin gereken tum verileri toplar -- bkz. EncounterReportPdfRenderer.
 */
@Service
@RequiredArgsConstructor
public class GenerateEncounterReportUseCase {

    private final EncounterRepository encounterRepository;
    private final PatientLookupPort patientLookupPort;
    private final OwnerLookupPort ownerLookupPort;
    private final StaffUserLookupPort staffUserLookupPort;
    private final TenantLookupPort tenantLookupPort;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final DrugCatalogRepository drugCatalogRepository;

    @Transactional(readOnly = true)
    public EncounterReportData execute(UUID encounterId) {
        Encounter encounter = encounterRepository.findById(encounterId)
            .orElseThrow(() -> new EncounterNotFoundException(encounterId));

        PatientSummary patient = patientLookupPort.findSummaryById(encounter.getPatientId());
        var owner = ownerLookupPort.findSummaryById(patient.ownerId());
        var staff = staffUserLookupPort.findSummaryById(encounter.getStaffUserId());
        String clinicName = tenantLookupPort.findTenantName(encounter.getTenantId()).orElse("Veteriner Klinigi");

        List<EncounterReportData.PrescriptionLine> prescriptionLines = new ArrayList<>();
        for (Prescription prescription : prescriptionRepository.findByEncounterId(encounterId)) {
            if (prescription.getStatus() == PrescriptionStatus.CANCELLED) continue;
            for (PrescriptionItem item : prescriptionItemRepository.findByPrescriptionId(prescription.getId())) {
                String drugName = drugCatalogRepository.findById(item.getDrugId())
                    .map(d -> d.getName())
                    .orElse("Bilinmeyen ilac");
                prescriptionLines.add(new EncounterReportData.PrescriptionLine(
                    drugName, item.getDosage(), item.getFrequency(), item.getDurationDays(), item.getRoute()
                ));
            }
        }

        return new EncounterReportData(
            clinicName, patient.name(), patient.speciesName(), owner.fullName(), owner.phone(), staff.fullName(),
            encounter.getEncounterDate(), encounter.getWeightKg(), encounter.getTemperatureC(),
            encounter.getHeartRate(), encounter.getRespiratoryRate(), encounter.getSubjective(), encounter.getObjective(),
            encounter.getAssessment(), encounter.getPlan(), encounter.getPhysicalExamFindings(), prescriptionLines,
            Instant.now()
        );
    }
}
