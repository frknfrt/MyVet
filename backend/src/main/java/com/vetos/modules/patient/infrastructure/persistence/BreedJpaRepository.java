package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.Breed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface BreedJpaRepository extends JpaRepository<Breed, UUID> {
    List<Breed> findBySpeciesId(UUID speciesId);
}
