package com.vetos.modules.encounter.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VaccinationRecordRepository {
    VaccinationRecord save(VaccinationRecord record);
    Optional<VaccinationRecord> findById(UUID id);
    List<VaccinationRecord> findByPatientId(UUID patientId);
    List<VaccinationRecord> findByTenantId(UUID tenantId);
}
