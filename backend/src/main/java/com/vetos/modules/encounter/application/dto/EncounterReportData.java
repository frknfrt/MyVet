package com.vetos.modules.encounter.application.dto;

import com.vetos.modules.encounter.domain.DrugRoute;
import com.vetos.modules.encounter.domain.PhysicalExamFinding;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Tek bir muayeneyi (encounter) hasta sahibine yonelik "Muayene Raporu" PDF'i
 * olarak sunmak icin gereken tum bilgileri bir araya toplar.
 * @docs/requirements.md 4.3 "tani/tedavi rapor halinde ciktisi, musteriye
 * bilgi verilmesi adina" -- tek muayene/ziyaret bazli kapsam.
 */
public record EncounterReportData(
    String clinicName,
    String patientName,
    String speciesName,
    String ownerFullName,
    String ownerPhone,
    String staffName,
    Instant encounterDate,
    BigDecimal weightKg,
    BigDecimal temperatureC,
    Integer heartRate,
    Integer respiratoryRate,
    String subjective,
    String objective,
    String assessment,
    String plan,
    List<PhysicalExamFinding> physicalExamFindings,
    List<PrescriptionLine> prescriptionLines,
    Instant generatedAt
) {
    public record PrescriptionLine(String drugName, String dosage, String frequency, int durationDays, DrugRoute route) {}
}
