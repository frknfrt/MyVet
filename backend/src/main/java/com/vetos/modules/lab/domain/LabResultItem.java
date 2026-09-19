package com.vetos.modules.lab.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "lab_result_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabResultItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "lab_result_id", nullable = false)
    private UUID labResultId;

    @Column(name = "parameter_name", nullable = false)
    private String parameterName;

    @Column(nullable = false)
    private String value;

    private String unit;

    @Column(name = "reference_range")
    private String referenceRange;

    @Enumerated(EnumType.STRING)
    private LabValueFlag flag;

    public static LabResultItem create(UUID tenantId, UUID labResultId, String parameterName, String value, String unit, String referenceRange, LabValueFlag flag) {
        LabResultItem item = new LabResultItem();
        item.tenantId = tenantId;
        item.labResultId = labResultId;
        item.parameterName = parameterName;
        item.value = value;
        item.unit = unit;
        item.referenceRange = referenceRange;
        item.flag = flag;
        return item;
    }
}
