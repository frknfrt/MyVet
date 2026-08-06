package com.vetos.modules.boarding.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "boarding_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardingRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "group_name", nullable = false)
    private String groupName;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "daily_rate")
    private BigDecimal dailyRate;

    private String notes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static BoardingRoom create(
        UUID tenantId, UUID branchId, String groupName, String name, int capacity, BigDecimal dailyRate, String notes
    ) {
        BoardingRoom room = new BoardingRoom();
        room.tenantId = tenantId;
        room.branchId = branchId;
        room.groupName = groupName;
        room.name = name;
        room.capacity = capacity;
        room.dailyRate = dailyRate;
        room.notes = notes;
        room.active = true;
        room.createdAt = Instant.now();
        return room;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}
