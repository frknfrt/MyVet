package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.EncounterDetail;
import com.vetos.modules.encounter.domain.EncounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListEncountersByPatientUseCase {

    private final EncounterRepository encounterRepository;
    private final GetEncounterUseCase getEncounterUseCase;

    @Transactional(readOnly = true)
    public List<EncounterDetail> execute(UUID patientId) {
        return encounterRepository.findByPatientId(patientId).stream()
            .map(getEncounterUseCase::toDetail)
            .sorted(Comparator.comparing(EncounterDetail::encounterDate).reversed())
            .toList();
    }
}
