package com.vetos.modules.lab.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabResultRepository {
    LabResult save(LabResult labResult);
    Optional<LabResult> findById(UUID id);
    List<LabResult> findByTenantId(UUID tenantId);
    List<LabResult> findByPatientId(UUID patientId);
}
