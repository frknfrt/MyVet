package com.vetos.modules.integration.tarbil.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tarbil_stock_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarbilStockSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tarbil_system", nullable = false)
    private TarbilStockSystem tarbilSystem;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    @Column(name = "taken_by_staff_id", nullable = false)
    private UUID takenByStaffId;

    public static TarbilStockSnapshot take(UUID tenantId, TarbilStockSystem system, UUID staffId, Instant now) {
        TarbilStockSnapshot snapshot = new TarbilStockSnapshot();
        snapshot.tenantId = tenantId;
        snapshot.tarbilSystem = system;
        snapshot.takenByStaffId = staffId;
        snapshot.takenAt = now;
        return snapshot;
    }
}
