package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.VaccinationRecordSummary;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListVaccinationsByPatientUseCase {

    private final VaccinationRecordRepository vaccinationRecordRepository;

    @Transactional(readOnly = true)
    public List<VaccinationRecordSummary> execute(UUID patientId) {
        return vaccinationRecordRepository.findByPatientId(patientId).stream()
            .map(r -> new VaccinationRecordSummary(r.getId(), r.getVaccineName(), r.getLotNumber(), r.getAdministeredDate(), r.getNextDueDate()))
            .toList();
    }
}
