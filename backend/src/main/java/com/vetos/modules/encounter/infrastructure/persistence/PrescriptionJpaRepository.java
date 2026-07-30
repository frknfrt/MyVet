package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface PrescriptionJpaRepository extends JpaRepository<Prescription, UUID> {
    List<Prescription> findByPatientId(UUID patientId);
}
