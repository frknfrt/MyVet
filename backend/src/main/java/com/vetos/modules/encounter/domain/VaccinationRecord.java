package com.vetos.modules.encounter.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "vaccination_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VaccinationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "vaccine_name", nullable = false)
    private String vaccineName;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "administered_date", nullable = false)
    private LocalDate administeredDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "administered_by_staff_id", nullable = false)
    private UUID administeredByStaffId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VaccinationStatus status;

    private String notes;

    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent;

    /** Stoktan secilen asi (spec 2026-10-04 P2): inventory modulundeki kalem; serbest yazilmis asida null. */
    @Column(name = "inventory_item_id")
    private UUID inventoryItemId;

    public static VaccinationRecord record(
        UUID tenantId, UUID patientId, UUID encounterId, String vaccineName, String lotNumber,
        LocalDate administeredDate, LocalDate nextDueDate, UUID administeredByStaffId,
        VaccinationStatus status, String notes
    ) {
        return record(tenantId, patientId, encounterId, vaccineName, lotNumber, administeredDate, nextDueDate,
            administeredByStaffId, status, notes, null);
    }

    public static VaccinationRecord record(
        UUID tenantId, UUID patientId, UUID encounterId, String vaccineName, String lotNumber,
        LocalDate administeredDate, LocalDate nextDueDate, UUID administeredByStaffId,
        VaccinationStatus status, String notes, UUID inventoryItemId
    ) {
        VaccinationRecord record = new VaccinationRecord();
        record.tenantId = tenantId;
        record.patientId = patientId;
        record.encounterId = encounterId;
        record.vaccineName = vaccineName;
        record.lotNumber = lotNumber;
        record.administeredDate = administeredDate;
        record.nextDueDate = nextDueDate;
        record.administeredByStaffId = administeredByStaffId;
        record.status = status;
        record.notes = notes;
        record.reminderSent = false;
        record.inventoryItemId = inventoryItemId;
        return record;
    }

    public void markAdministered(LocalDate administeredDate) {
        this.status = VaccinationStatus.ADMINISTERED;
        this.administeredDate = administeredDate;
    }

    public void cancel() {
        this.status = VaccinationStatus.CANCELLED;
    }

    public void markReminderSent() {
        this.reminderSent = true;
    }
}
