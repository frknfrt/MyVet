package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.SpeciesSummary;
import com.vetos.modules.patient.domain.SpeciesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListSpeciesUseCase {

    private final SpeciesRepository speciesRepository;

    @Transactional(readOnly = true)
    public List<SpeciesSummary> execute() {
        return speciesRepository.findAll().stream()
            .map(species -> new SpeciesSummary(species.getId(), species.getName()))
            .toList();
    }
}
