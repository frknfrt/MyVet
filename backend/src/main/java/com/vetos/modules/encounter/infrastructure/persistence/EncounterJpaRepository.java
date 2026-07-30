package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface EncounterJpaRepository extends JpaRepository<Encounter, UUID> {
    List<Encounter> findByPatientId(UUID patientId);
}
