package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.BreedSummary;
import com.vetos.modules.patient.domain.BreedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListBreedsBySpeciesUseCase {

    private final BreedRepository breedRepository;

    @Transactional(readOnly = true)
    public List<BreedSummary> execute(UUID speciesId) {
        return breedRepository.findBySpeciesId(speciesId).stream()
            .map(breed -> new BreedSummary(breed.getId(), breed.getName()))
            .toList();
    }
}
