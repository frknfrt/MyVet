package com.vetos.modules.patient.application;

import com.vetos.modules.patient.domain.Species;
import com.vetos.modules.patient.domain.SpeciesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateSpeciesUseCase {

    private final SpeciesRepository speciesRepository;

    @Transactional
    public UUID execute(String name) {
        return speciesRepository.save(Species.create(name)).getId();
    }
}
