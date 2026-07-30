package com.vetos.modules.patient.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository {
    Patient save(Patient patient);
    Optional<Patient> findById(UUID id);
    List<Patient> findByOwnerId(UUID ownerId);
    List<Patient> searchByNameOrOwner(UUID tenantId, String query);
}
