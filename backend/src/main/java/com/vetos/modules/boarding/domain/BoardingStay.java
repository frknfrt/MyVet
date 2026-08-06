package com.vetos.modules.boarding.domain;

import com.vetos.modules.boarding.domain.exception.BoardingStayInvalidStateException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "boarding_stays")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardingStay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "created_by_staff_id")
    private UUID createdByStaffId;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "expected_check_out_date")
    private LocalDate expectedCheckOutDate;

    @Column(name = "actual_check_out_date")
    private LocalDate actualCheckOutDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoardingStayStatus status;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static BoardingStay create(
        UUID tenantId, UUID branchId, UUID roomId, UUID patientId, UUID ownerId, UUID createdByStaffId,
        LocalDate checkInDate, LocalDate expectedCheckOutDate, String notes
    ) {
        BoardingStay stay = new BoardingStay();
        stay.tenantId = tenantId;
        stay.branchId = branchId;
        stay.roomId = roomId;
        stay.patientId = patientId;
        stay.ownerId = ownerId;
        stay.createdByStaffId = createdByStaffId;
        stay.checkInDate = checkInDate;
        stay.expectedCheckOutDate = expectedCheckOutDate;
        stay.notes = notes;
        stay.status = BoardingStayStatus.CHECKED_IN;
        stay.createdAt = Instant.now();
        return stay;
    }

    public void checkOut(LocalDate actualDate) {
        if (status != BoardingStayStatus.CHECKED_IN) {
            throw new BoardingStayInvalidStateException(id, "Sadece konaklamada olan kayit cikis yapabilir, mevcut durum: " + status);
        }
        this.status = BoardingStayStatus.CHECKED_OUT;
        this.actualCheckOutDate = actualDate != null ? actualDate : LocalDate.now();
    }

    public void cancel() {
        if (status != BoardingStayStatus.CHECKED_IN) {
            throw new BoardingStayInvalidStateException(id, "Sadece konaklamada olan kayit iptal edilebilir, mevcut durum: " + status);
        }
        this.status = BoardingStayStatus.CANCELLED;
    }
}
