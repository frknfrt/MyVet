package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.VaccinationScheduleItem;
import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.OwnerSummary;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListVaccinationsByPatientUseCase {

    private final VaccinationRecordRepository vaccinationRecordRepository;
    private final PatientLookupPort patientLookupPort;
    private final OwnerLookupPort ownerLookupPort;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional(readOnly = true)
    public List<VaccinationScheduleItem> execute(UUID patientId) {
        var patient = patientLookupPort.findSummaryById(patientId);
        var owner = ownerLookupPort.findSummaryById(patient.ownerId());

        return vaccinationRecordRepository.findByPatientId(patientId).stream()
            .map(record -> toItem(record, patient.name(), owner))
            .sorted(Comparator.comparing(VaccinationScheduleItem::administeredDate).reversed())
            .toList();
    }

    private VaccinationScheduleItem toItem(VaccinationRecord record, String patientName, OwnerSummary owner) {
        String staffName = staffUserLookupPort.findSummaryById(record.getAdministeredByStaffId()).fullName();

        return new VaccinationScheduleItem(
            record.getId(), record.getPatientId(), patientName, owner.id(), owner.fullName(),
            record.getVaccineName(), record.getLotNumber(), record.getAdministeredDate(), record.getNextDueDate(),
            record.getStatus(), record.getNotes(), staffName
        );
    }
}
