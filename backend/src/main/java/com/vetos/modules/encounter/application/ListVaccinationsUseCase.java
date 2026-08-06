package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.VaccinationScheduleItem;
import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.patient.domain.OwnerLookupPort;
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
public class ListVaccinationsUseCase {

    private final VaccinationRecordRepository vaccinationRecordRepository;
    private final PatientLookupPort patientLookupPort;
    private final OwnerLookupPort ownerLookupPort;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional(readOnly = true)
    public List<VaccinationScheduleItem> execute(UUID tenantId) {
        return vaccinationRecordRepository.findByTenantId(tenantId).stream()
            .map(this::toItem)
            .sorted(Comparator.comparing(VaccinationScheduleItem::administeredDate).reversed())
            .toList();
    }

    private VaccinationScheduleItem toItem(VaccinationRecord record) {
        var patient = patientLookupPort.findSummaryById(record.getPatientId());
        var owner = ownerLookupPort.findSummaryById(patient.ownerId());
        String staffName = staffUserLookupPort.findSummaryById(record.getAdministeredByStaffId()).fullName();

        return new VaccinationScheduleItem(
            record.getId(), patient.id(), patient.name(), owner.id(), owner.fullName(),
            record.getVaccineName(), record.getLotNumber(), record.getAdministeredDate(), record.getNextDueDate(),
            record.getStatus(), record.getNotes(), staffName
        );
    }
}
