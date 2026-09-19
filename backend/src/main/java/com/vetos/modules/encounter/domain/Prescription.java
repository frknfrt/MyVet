package com.vetos.modules.encounter.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "prescriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "prescribing_staff_id", nullable = false)
    private UUID prescribingStaffId;

    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrescriptionStatus status;

    @Column(name = "controlled_substance", nullable = false)
    private boolean controlledSubstance;

    @Column(name = "pharmacy_integration_ref")
    private String pharmacyIntegrationRef;

    public static Prescription issue(
        UUID tenantId, UUID patientId, UUID encounterId, UUID prescribingStaffId, boolean controlledSubstance
    ) {
        Prescription prescription = new Prescription();
        prescription.tenantId = tenantId;
        prescription.patientId = patientId;
        prescription.encounterId = encounterId;
        prescription.prescribingStaffId = prescribingStaffId;
        prescription.issuedDate = LocalDate.now();
        prescription.status = PrescriptionStatus.ACTIVE;
        prescription.controlledSubstance = controlledSubstance;
        return prescription;
    }

    public void markFulfilled() {
        this.status = PrescriptionStatus.FULFILLED;
    }

    public void cancel() {
        this.status = PrescriptionStatus.CANCELLED;
    }
}
