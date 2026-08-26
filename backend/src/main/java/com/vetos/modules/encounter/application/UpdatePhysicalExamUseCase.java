package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.UpdatePhysicalExamCommand;
import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdatePhysicalExamUseCase {

    private final EncounterRepository encounterRepository;

    @Transactional
    public void execute(UpdatePhysicalExamCommand command) {
        Encounter encounter = encounterRepository.findById(command.encounterId())
            .orElseThrow(() -> new EncounterNotFoundException(command.encounterId()));
        encounter.updatePhysicalExam(command.findings());
        encounterRepository.save(encounter);
    }
}
