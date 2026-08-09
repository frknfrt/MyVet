package com.vetos.modules.tenant.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "branch_working_hours")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchWorkingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private boolean closed;

    @Column(name = "opens_at")
    private LocalTime opensAt;

    @Column(name = "closes_at")
    private LocalTime closesAt;

    public static BranchWorkingHours open(UUID branchId, DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        BranchWorkingHours entry = new BranchWorkingHours();
        entry.branchId = branchId;
        entry.dayOfWeek = dayOfWeek;
        entry.closed = false;
        entry.opensAt = opensAt;
        entry.closesAt = closesAt;
        return entry;
    }

    public static BranchWorkingHours closedDay(UUID branchId, DayOfWeek dayOfWeek) {
        BranchWorkingHours entry = new BranchWorkingHours();
        entry.branchId = branchId;
        entry.dayOfWeek = dayOfWeek;
        entry.closed = true;
        return entry;
    }
}
