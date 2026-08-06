package com.vetos.modules.imaging.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImagingRecordRepository {
    ImagingRecord save(ImagingRecord record);
    Optional<ImagingRecord> findById(UUID id);
    List<ImagingRecord> findByTenantId(UUID tenantId);
    List<ImagingRecord> findByPatientId(UUID patientId);
}
