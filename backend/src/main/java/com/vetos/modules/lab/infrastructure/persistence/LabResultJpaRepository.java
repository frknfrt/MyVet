package com.vetos.modules.lab.infrastructure.persistence;

import com.vetos.modules.lab.domain.LabResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

interface LabResultJpaRepository extends JpaRepository<LabResult, UUID> {
    List<LabResult> findByTenantId(UUID tenantId);
    List<LabResult> findByPatientId(UUID patientId);

    /** AI hasta geçmişi (2026-10-25): tarih = sonuç ?? istek; tarih >= from ve istek < requestedBefore; en yeniden eskiye. */
    @Query("""
        select r from LabResult r
        where r.patientId = :patientId and r.status = com.vetos.modules.lab.domain.LabResultStatus.COMPLETED
          and r.requestedAt < :requestedBefore and coalesce(r.resultedAt, r.requestedAt) >= :from
        order by coalesce(r.resultedAt, r.requestedAt) desc""")
    List<LabResult> findCompletedHistory(UUID patientId, Instant from, Instant requestedBefore,
                                         Pageable page);
}
