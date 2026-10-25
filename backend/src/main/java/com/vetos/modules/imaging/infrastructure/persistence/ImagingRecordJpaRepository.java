package com.vetos.modules.imaging.infrastructure.persistence;

import com.vetos.modules.imaging.domain.ImagingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

interface ImagingRecordJpaRepository extends JpaRepository<ImagingRecord, UUID> {
    List<ImagingRecord> findByTenantId(UUID tenantId);
    List<ImagingRecord> findByPatientId(UUID patientId);

    /** AI hasta geçmişi (2026-10-25): tarih = sonuç ?? istek; tarih >= from ve istek < requestedBefore; en yeniden eskiye. */
    @Query("""
        select r from ImagingRecord r
        where r.patientId = :patientId and r.status = com.vetos.modules.imaging.domain.ImagingRecordStatus.COMPLETED
          and r.requestedAt < :requestedBefore and coalesce(r.resultedAt, r.requestedAt) >= :from
        order by coalesce(r.resultedAt, r.requestedAt) desc""")
    List<ImagingRecord> findCompletedHistory(UUID patientId, Instant from, Instant requestedBefore,
                                         Pageable page);
}
