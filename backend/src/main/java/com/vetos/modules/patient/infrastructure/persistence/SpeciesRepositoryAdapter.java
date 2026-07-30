package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.Species;
import com.vetos.modules.patient.domain.SpeciesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class SpeciesRepositoryAdapter implements SpeciesRepository {

    private final SpeciesJpaRepository jpaRepository;

    @Override
    public Species save(Species species) { return jpaRepository.save(species); }

    @Override
    public Optional<Species> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<Species> findAll() { return jpaRepository.findAll(); }
}
