package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.StartEncounterCommand;
import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StartEncounterUseCase {

    private final EncounterRepository encounterRepository;

    @Transactional
    public UUID execute(StartEncounterCommand command) {
        Encounter encounter = Encounter.start(
            TenantContext.current(), command.patientId(), command.staffUserId(),
            command.appointmentId(), command.templateUsed()
        );
        return encounterRepository.save(encounter).getId();
    }
}
