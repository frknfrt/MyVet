package com.vetos.modules.imaging.infrastructure.persistence;

import com.vetos.modules.imaging.domain.ImagingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ImagingRecordJpaRepository extends JpaRepository<ImagingRecord, UUID> {
    List<ImagingRecord> findByTenantId(UUID tenantId);
    List<ImagingRecord> findByPatientId(UUID patientId);
}
