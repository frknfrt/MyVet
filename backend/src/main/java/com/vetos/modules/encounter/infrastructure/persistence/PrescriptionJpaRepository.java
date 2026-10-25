package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import com.vetos.modules.encounter.domain.PrescriptionStatus;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;

interface PrescriptionJpaRepository extends JpaRepository<Prescription, UUID> {
    List<Prescription> findByPatientId(UUID patientId);
    List<Prescription> findByEncounterId(UUID encounterId);
    /** AI hasta geçmişi: tarih aralığı ve üst sınır veritabanında (2026-10-25). */
    List<Prescription> findByPatientIdAndStatusNotAndIssuedDateBetweenOrderByIssuedDateDesc(
        UUID patientId, PrescriptionStatus excluded, LocalDate from, LocalDate to,
        Pageable page);
}
