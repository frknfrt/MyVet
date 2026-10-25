package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

interface VaccinationRecordJpaRepository extends JpaRepository<VaccinationRecord, UUID> {
    List<VaccinationRecord> findByPatientId(UUID patientId);
    /** AI hasta geçmişi: tarih aralığı ve üst sınır veritabanında (2026-10-25). */
    List<VaccinationRecord> findByPatientIdAndStatusAndAdministeredDateBetweenOrderByAdministeredDateDesc(
        UUID patientId, VaccinationStatus status, LocalDate from, LocalDate to, Pageable page);
    List<VaccinationRecord> findByTenantId(UUID tenantId);
    List<VaccinationRecord> findBySeriesId(UUID seriesId);

    /** Gunluk asi hatirlatma isi icin -- bkz. VaccinationLookupAdapter. */
    List<VaccinationRecord> findByTenantIdAndNextDueDateAndStatusNot(UUID tenantId, LocalDate nextDueDate, VaccinationStatus excludedStatus);
}
