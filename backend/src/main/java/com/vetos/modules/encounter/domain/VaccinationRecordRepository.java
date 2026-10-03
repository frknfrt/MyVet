package com.vetos.modules.encounter.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VaccinationRecordRepository {
    VaccinationRecord save(VaccinationRecord record);
    List<VaccinationRecord> saveAll(List<VaccinationRecord> records);
    Optional<VaccinationRecord> findById(UUID id);
    List<VaccinationRecord> findByPatientId(UUID patientId);
    List<VaccinationRecord> findByTenantId(UUID tenantId);
    List<VaccinationRecord> findBySeriesId(UUID seriesId);
}
