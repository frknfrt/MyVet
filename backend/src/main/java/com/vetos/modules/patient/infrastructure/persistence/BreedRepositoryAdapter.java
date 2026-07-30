package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.Breed;
import com.vetos.modules.patient.domain.BreedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class BreedRepositoryAdapter implements BreedRepository {

    private final BreedJpaRepository jpaRepository;

    @Override
    public Breed save(Breed breed) { return jpaRepository.save(breed); }

    @Override
    public Optional<Breed> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<Breed> findBySpeciesId(UUID speciesId) { return jpaRepository.findBySpeciesId(speciesId); }
}
