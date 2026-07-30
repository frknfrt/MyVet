package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.EncounterDetail;
import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetEncounterUseCase {

    private final EncounterRepository encounterRepository;
    private final PatientLookupPort patientLookupPort;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional(readOnly = true)
    public EncounterDetail execute(UUID encounterId) {
        Encounter encounter = encounterRepository.findById(encounterId)
            .orElseThrow(() -> new EncounterNotFoundException(encounterId));
        return toDetail(encounter);
    }

    EncounterDetail toDetail(Encounter encounter) {
        var patient = patientLookupPort.findSummaryById(encounter.getPatientId());
        var staff = staffUserLookupPort.findSummaryById(encounter.getStaffUserId());

        return new EncounterDetail(
            encounter.getId(), encounter.getPatientId(), patient.name(), encounter.getStaffUserId(), staff.fullName(),
            encounter.getAppointmentId(), encounter.getEncounterDate(), encounter.getSubjective(), encounter.getObjective(),
            encounter.getAssessment(), encounter.getPlan(), encounter.getWeightKg(), encounter.getTemperatureC(),
            encounter.getHeartRate(), encounter.getRespiratoryRate(), encounter.getTemplateUsed(), encounter.getStatus(),
            encounter.isAiGenerated(), encounter.getFinalizedAt()
        );
    }
}
