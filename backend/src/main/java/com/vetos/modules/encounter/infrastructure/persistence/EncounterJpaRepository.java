package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface EncounterJpaRepository extends JpaRepository<Encounter, UUID> {
    List<Encounter> findByPatientId(UUID patientId);
    List<Encounter> findByStatus(EncounterStatus status);
    /** Randevunun iptal edilmemis en son muayenesi; iptal edilen yerine yenisi acilabilir. */
    Optional<Encounter> findFirstByAppointmentIdAndStatusNotOrderByEncounterDateDesc(UUID appointmentId, EncounterStatus excluded);
}
