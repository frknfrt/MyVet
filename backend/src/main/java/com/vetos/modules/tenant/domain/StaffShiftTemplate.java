package com.vetos.modules.tenant.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "staff_shift_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffShiftTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "starts_at", nullable = false)
    private LocalTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalTime endsAt;

    public static StaffShiftTemplate create(UUID staffUserId, DayOfWeek dayOfWeek, LocalTime startsAt, LocalTime endsAt) {
        StaffShiftTemplate shift = new StaffShiftTemplate();
        shift.staffUserId = staffUserId;
        shift.dayOfWeek = dayOfWeek;
        shift.startsAt = startsAt;
        shift.endsAt = endsAt;
        return shift;
    }
}
