package com.vetos.modules.patient.application;

import com.vetos.modules.patient.domain.Breed;
import com.vetos.modules.patient.domain.BreedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateBreedUseCase {

    private final BreedRepository breedRepository;

    @Transactional
    public UUID execute(UUID speciesId, String name) {
        return breedRepository.save(Breed.create(speciesId, name)).getId();
    }
}
