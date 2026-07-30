package com.vetos.modules.patient.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpeciesRepository {
    Species save(Species species);
    Optional<Species> findById(UUID id);
    List<Species> findAll();
}
