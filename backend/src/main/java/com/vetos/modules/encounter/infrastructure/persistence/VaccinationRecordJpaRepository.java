package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface VaccinationRecordJpaRepository extends JpaRepository<VaccinationRecord, UUID> {
    List<VaccinationRecord> findByPatientId(UUID patientId);
    List<VaccinationRecord> findByTenantId(UUID tenantId);
    List<VaccinationRecord> findBySeriesId(UUID seriesId);

    /** Gunluk asi hatirlatma isi icin -- bkz. VaccinationLookupAdapter. */
    List<VaccinationRecord> findByTenantIdAndNextDueDateAndStatusNot(UUID tenantId, LocalDate nextDueDate, VaccinationStatus excludedStatus);
}
