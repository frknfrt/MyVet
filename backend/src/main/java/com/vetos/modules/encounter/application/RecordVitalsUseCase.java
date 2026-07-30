package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.RecordVitalsCommand;
import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordVitalsUseCase {

    private final EncounterRepository encounterRepository;

    @Transactional
    public void execute(RecordVitalsCommand command) {
        Encounter encounter = encounterRepository.findById(command.encounterId())
            .orElseThrow(() -> new EncounterNotFoundException(command.encounterId()));
        encounter.recordVitals(command.weightKg(), command.temperatureC(), command.heartRate(), command.respiratoryRate());
        encounterRepository.save(encounter);
    }
}
