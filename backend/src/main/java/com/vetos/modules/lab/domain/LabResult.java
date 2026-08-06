package com.vetos.modules.lab.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lab_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "ordering_staff_id")
    private UUID orderingStaffId;

    @Column(name = "test_name", nullable = false)
    private String testName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LabResultStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "resulted_at")
    private Instant resultedAt;

    @Column(name = "result_summary")
    private String resultSummary;

    private String notes;

    public static LabResult request(UUID tenantId, UUID patientId, UUID orderingStaffId, String testName, String notes) {
        LabResult result = new LabResult();
        result.tenantId = tenantId;
        result.patientId = patientId;
        result.orderingStaffId = orderingStaffId;
        result.testName = testName;
        result.notes = notes;
        result.status = LabResultStatus.PENDING;
        result.requestedAt = Instant.now();
        return result;
    }

    public void complete(String resultSummary) {
        this.status = LabResultStatus.COMPLETED;
        this.resultSummary = resultSummary;
        this.resultedAt = Instant.now();
    }

    public void cancel() {
        this.status = LabResultStatus.CANCELLED;
    }
}
