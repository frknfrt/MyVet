package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.Prescription;
import com.vetos.modules.encounter.domain.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PrescriptionRepositoryAdapter implements PrescriptionRepository {

    private final PrescriptionJpaRepository jpaRepository;

    @Override
    public Prescription save(Prescription prescription) { return jpaRepository.save(prescription); }

    @Override
    public Optional<Prescription> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<Prescription> findByPatientId(UUID patientId) { return jpaRepository.findByPatientId(patientId); }
}
