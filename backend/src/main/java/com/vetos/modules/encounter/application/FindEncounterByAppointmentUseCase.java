package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.EncounterDetail;
import com.vetos.modules.encounter.domain.EncounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Randevu Takvimi'nden "SOAP'a Git" ile var olan muayeneye donmek icin
 * (@docs/implementation-plan.md sonrasi eklenen kesfedilebilirlik duzeltmesi).
 */
@Service
@RequiredArgsConstructor
public class FindEncounterByAppointmentUseCase {

    private final EncounterRepository encounterRepository;
    private final GetEncounterUseCase getEncounterUseCase;

    @Transactional(readOnly = true)
    public Optional<EncounterDetail> execute(UUID appointmentId) {
        return encounterRepository.findByAppointmentId(appointmentId).map(getEncounterUseCase::toDetail);
    }
}
