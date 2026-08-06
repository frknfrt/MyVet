package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.encounter.domain.exception.VaccinationRecordNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CancelVaccinationUseCase {

    private final VaccinationRecordRepository vaccinationRecordRepository;

    @Transactional
    public void execute(UUID vaccinationRecordId) {
        VaccinationRecord record = vaccinationRecordRepository.findById(vaccinationRecordId)
            .orElseThrow(() -> new VaccinationRecordNotFoundException(vaccinationRecordId));
        record.cancel();
        vaccinationRecordRepository.save(record);
    }
}
