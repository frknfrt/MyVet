package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.Species;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpeciesJpaRepository extends JpaRepository<Species, UUID> {
}
