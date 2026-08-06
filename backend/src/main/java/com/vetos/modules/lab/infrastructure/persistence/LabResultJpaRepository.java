package com.vetos.modules.lab.infrastructure.persistence;

import com.vetos.modules.lab.domain.LabResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface LabResultJpaRepository extends JpaRepository<LabResult, UUID> {
    List<LabResult> findByTenantId(UUID tenantId);
    List<LabResult> findByPatientId(UUID patientId);
}
