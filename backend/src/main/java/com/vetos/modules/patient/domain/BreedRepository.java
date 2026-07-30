package com.vetos.modules.patient.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BreedRepository {
    Breed save(Breed breed);
    Optional<Breed> findById(UUID id);
    List<Breed> findBySpeciesId(UUID speciesId);
}
