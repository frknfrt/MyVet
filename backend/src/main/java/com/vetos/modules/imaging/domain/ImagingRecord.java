package com.vetos.modules.imaging.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "imaging_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImagingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "ordering_staff_id")
    private UUID orderingStaffId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImagingModality modality;

    @Column(name = "body_region")
    private String bodyRegion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImagingRecordStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "resulted_at")
    private Instant resultedAt;

    private String findings;

    private String notes;

    public static ImagingRecord request(UUID tenantId, UUID patientId, UUID orderingStaffId, ImagingModality modality, String bodyRegion, String notes) {
        ImagingRecord record = new ImagingRecord();
        record.tenantId = tenantId;
        record.patientId = patientId;
        record.orderingStaffId = orderingStaffId;
        record.modality = modality;
        record.bodyRegion = bodyRegion;
        record.notes = notes;
        record.status = ImagingRecordStatus.PENDING;
        record.requestedAt = Instant.now();
        return record;
    }

    public void complete(String findings) {
        this.status = ImagingRecordStatus.COMPLETED;
        this.findings = findings;
        this.resultedAt = Instant.now();
    }

    public void cancel() {
        this.status = ImagingRecordStatus.CANCELLED;
    }
}
