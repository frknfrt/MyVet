package com.vetos.modules.appointment.domain;

import com.vetos.modules.appointment.domain.exception.AppointmentInvalidTransitionException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "assigned_staff_id")
    private UUID assignedStaffId;

    @Column(name = "service_type_id", nullable = false)
    private UUID serviceTypeId;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private Instant scheduledEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(name = "no_show_risk_score")
    private BigDecimal noShowRiskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentSource source;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Appointment schedule(
        UUID tenantId, UUID branchId, UUID patientId, UUID ownerId, UUID assignedStaffId, UUID serviceTypeId,
        Instant scheduledStart, Instant scheduledEnd, AppointmentSource source, String notes
    ) {
        Appointment appointment = new Appointment();
        appointment.tenantId = tenantId;
        appointment.branchId = branchId;
        appointment.patientId = patientId;
        appointment.ownerId = ownerId;
        appointment.assignedStaffId = assignedStaffId;
        appointment.serviceTypeId = serviceTypeId;
        appointment.scheduledStart = scheduledStart;
        appointment.scheduledEnd = scheduledEnd;
        appointment.source = source;
        appointment.notes = notes;
        appointment.status = source == AppointmentSource.WIDGET ? AppointmentStatus.REQUESTED : AppointmentStatus.CONFIRMED;
        appointment.createdAt = Instant.now();
        return appointment;
    }

    public void assignNoShowRiskScore(BigDecimal score) {
        this.noShowRiskScore = score;
    }

    /** WIDGET kaynakli, henuz hekim atanmamis talepler icin (Modul 8: online randevu widget'i). */
    public void assignStaff(UUID staffId) {
        this.assignedStaffId = staffId;
    }

    public void confirm() {
        requireCurrentStatus(AppointmentStatus.REQUESTED, AppointmentStatus.CONFIRMED);
        this.status = AppointmentStatus.CONFIRMED;
    }

    public void checkIn() {
        requireCurrentStatus(AppointmentStatus.CONFIRMED, AppointmentStatus.CHECKED_IN);
        this.status = AppointmentStatus.CHECKED_IN;
    }

    public void start() {
        requireCurrentStatus(AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_PROGRESS);
        this.status = AppointmentStatus.IN_PROGRESS;
    }

    public void complete() {
        requireCurrentStatus(AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED);
        this.status = AppointmentStatus.COMPLETED;
    }

    public void markNoShow() {
        if (status != AppointmentStatus.CONFIRMED && status != AppointmentStatus.CHECKED_IN) {
            throw new AppointmentInvalidTransitionException(status, AppointmentStatus.NO_SHOW);
        }
        this.status = AppointmentStatus.NO_SHOW;
    }

    public void cancel() {
        if (status == AppointmentStatus.COMPLETED || status == AppointmentStatus.CANCELLED || status == AppointmentStatus.NO_SHOW) {
            throw new AppointmentInvalidTransitionException(status, AppointmentStatus.CANCELLED);
        }
        this.status = AppointmentStatus.CANCELLED;
    }

    private void requireCurrentStatus(AppointmentStatus expected, AppointmentStatus target) {
        if (this.status != expected) {
            throw new AppointmentInvalidTransitionException(this.status, target);
        }
    }
}
