package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PatientRepositoryAdapter implements PatientRepository {

    private final PatientJpaRepository jpaRepository;

    @Override
    public Patient save(Patient patient) { return jpaRepository.save(patient); }

    @Override
    public Optional<Patient> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<Patient> findByOwnerId(UUID ownerId) { return jpaRepository.findByOwnerId(ownerId); }

    @Override
    public List<Patient> searchByNameOrOwner(UUID tenantId, String query) {
        return jpaRepository.searchByNameOrOwner(tenantId, query);
    }
}
