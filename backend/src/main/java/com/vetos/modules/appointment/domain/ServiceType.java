package com.vetos.modules.appointment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "service_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "default_duration_min", nullable = false)
    private int defaultDurationMin;

    @Column(name = "default_price", nullable = false)
    private BigDecimal defaultPrice;

    public static ServiceType create(UUID tenantId, String name, int defaultDurationMin, BigDecimal defaultPrice) {
        ServiceType serviceType = new ServiceType();
        serviceType.tenantId = tenantId;
        serviceType.name = name;
        serviceType.defaultDurationMin = defaultDurationMin;
        serviceType.defaultPrice = defaultPrice;
        return serviceType;
    }

    public void updateDetails(String name, int defaultDurationMin, BigDecimal defaultPrice) {
        this.name = name;
        this.defaultDurationMin = defaultDurationMin;
        this.defaultPrice = defaultPrice;
    }
}
