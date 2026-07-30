package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.VaccinationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface VaccinationRecordJpaRepository extends JpaRepository<VaccinationRecord, UUID> {
    List<VaccinationRecord> findByPatientId(UUID patientId);
}
