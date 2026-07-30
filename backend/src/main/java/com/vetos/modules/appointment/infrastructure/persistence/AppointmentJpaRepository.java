package com.vetos.modules.appointment.infrastructure.persistence;

import com.vetos.modules.appointment.domain.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface AppointmentJpaRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByOwnerId(UUID ownerId);

    @Query(
        "SELECT a FROM Appointment a WHERE a.branchId = :branchId " +
            "AND a.scheduledStart >= :rangeStart AND a.scheduledStart < :rangeEnd " +
            "ORDER BY a.scheduledStart ASC"
    )
    List<Appointment> findByBranchAndDateRange(
        @Param("branchId") UUID branchId, @Param("rangeStart") Instant rangeStart, @Param("rangeEnd") Instant rangeEnd
    );

    @Query(
        "SELECT COUNT(a) > 0 FROM Appointment a WHERE a.assignedStaffId = :staffId " +
            "AND a.status IN ('CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS') " +
            "AND (:excludeId IS NULL OR a.id <> :excludeId) " +
            "AND a.scheduledStart < :end AND a.scheduledEnd > :start"
    )
    boolean existsOverlapping(
        @Param("staffId") UUID staffId, @Param("start") Instant start, @Param("end") Instant end,
        @Param("excludeId") UUID excludeAppointmentId
    );
}
