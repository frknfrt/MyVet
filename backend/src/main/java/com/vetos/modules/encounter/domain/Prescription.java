package com.vetos.modules.encounter.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/** Recete; muayeneye bagli olmak zorunda degil (spec 2026-10-09 S5.6). Stoktan verilen kalem varsa TARBIL teshisi tasir. */
@Entity
@Table(name = "prescriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prescription {

    @Id
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    /** Muayenesiz recetede null. */
    @Column(name = "encounter_id")
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

    /** tarbil_disease.id (integration modulune Java bagimliligi yok, yalniz deger). */
    @Column(name = "tarbil_disease_id")
    private UUID tarbilDiseaseId;

    public static Prescription issue(
        UUID tenantId, UUID patientId, UUID encounterId, UUID prescribingStaffId, boolean controlledSubstance
    ) {
        return issue(tenantId, patientId, encounterId, prescribingStaffId, controlledSubstance, null);
    }

    public static Prescription issue(
        UUID tenantId, UUID patientId, UUID encounterId, UUID prescribingStaffId, boolean controlledSubstance, UUID tarbilDiseaseId
    ) {
        Prescription prescription = new Prescription();
        prescription.id = UUID.randomUUID();
        prescription.tenantId = tenantId;
        prescription.patientId = patientId;
        prescription.encounterId = encounterId;
        prescription.prescribingStaffId = prescribingStaffId;
        prescription.issuedDate = LocalDate.now();
        prescription.status = PrescriptionStatus.ACTIVE;
        prescription.controlledSubstance = controlledSubstance;
        prescription.tarbilDiseaseId = tarbilDiseaseId;
        return prescription;
    }

    public void markFulfilled() {
        this.status = PrescriptionStatus.FULFILLED;
    }

    /** @return false ise recete aktif degildi (degisiklik yok). */
    public boolean cancel() {
        if (status != PrescriptionStatus.ACTIVE) {
            return false;
        }
        this.status = PrescriptionStatus.CANCELLED;
        return true;
    }
}
