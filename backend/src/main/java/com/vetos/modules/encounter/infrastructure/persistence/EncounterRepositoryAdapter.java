package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class EncounterRepositoryAdapter implements EncounterRepository {

    private final EncounterJpaRepository jpaRepository;

    @Override
    public Encounter save(Encounter encounter) { return jpaRepository.save(encounter); }

    @Override
    public Optional<Encounter> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<Encounter> findByPatientId(UUID patientId) { return jpaRepository.findByPatientId(patientId); }

    @Override
    public Optional<Encounter> findByAppointmentId(UUID appointmentId) { return jpaRepository.findByAppointmentId(appointmentId); }
}
