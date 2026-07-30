package com.vetos.modules.encounter.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EncounterRepository {
    Encounter save(Encounter encounter);
    Optional<Encounter> findById(UUID id);
    List<Encounter> findByPatientId(UUID patientId);
}
