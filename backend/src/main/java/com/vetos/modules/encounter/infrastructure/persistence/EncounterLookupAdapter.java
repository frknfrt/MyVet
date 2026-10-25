package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.*;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import java.util.Map;

@Component
@RequiredArgsConstructor
class EncounterLookupAdapter implements EncounterLookupPort {

    private final EncounterJpaRepository jpaRepository;
    private final PrescriptionJpaRepository prescriptionRepository;
    private final PrescriptionItemJpaRepository prescriptionItemRepository;
    private final DrugCatalogRepository drugCatalogRepository;

    @Override
    public EncounterSummary findSummaryById(UUID encounterId) {
        Encounter e = jpaRepository.findById(encounterId)
            .orElseThrow(() -> new EncounterNotFoundException(encounterId));
        return new EncounterSummary(e.getId(), e.getPatientId(), e.getStaffUserId(), e.getStatus());
    }

    @Override
    public EncounterClinicalContext findClinicalContext(UUID encounterId, int historyLimit) {
        Encounter current = jpaRepository.findById(encounterId)
            .orElseThrow(() -> new EncounterNotFoundException(encounterId));

        List<PastEncounterSummary> history = jpaRepository.findByPatientId(current.getPatientId()).stream()
            .filter(e -> !e.getId().equals(encounterId))
            .filter(e -> e.getStatus() == EncounterStatus.FINALIZED || e.getStatus() == EncounterStatus.AMENDED)
            // Bu muayeneden sonraki muayeneler "geçmiş" değil; sınırdan önce elenir (2026-10-25).
            .filter(e -> e.getEncounterDate() != null && !e.getEncounterDate().isAfter(current.getEncounterDate()))
            .sorted(Comparator.comparing(Encounter::getEncounterDate).reversed())
            .limit(historyLimit)
            .map(e -> new PastEncounterSummary(e.getEncounterDate(), e.getAssessment(), e.getPlan()))
            .toList();

        return new EncounterClinicalContext(
            current.getId(), current.getPatientId(), current.getAssessment(),
            current.getSubjective(), current.getObjective(),
            buildVitalsSummary(current), buildPhysicalExamSummary(current),
            history, current.getEncounterDate(), current.getWeightKg()
        );
    }

    @Override
    public List<PrescriptionHistoryEntry> findPrescriptionHistory(UUID patientId, LocalDate from, LocalDate to, int limit) {
        List<Prescription> found = prescriptionRepository.findByPatientIdAndStatusNotAndIssuedDateBetweenOrderByIssuedDateDesc(
            patientId, PrescriptionStatus.CANCELLED, from, to, PageRequest.of(0, limit));
        if (found.isEmpty()) return List.of();
        // Kalemler tek sorguda (2026-10-25).
        Map<UUID, List<PrescriptionItem>> byPrescription = prescriptionItemRepository
            .findByPrescriptionIdIn(found.stream().map(Prescription::getId).toList()).stream()
            .collect(Collectors.groupingBy(PrescriptionItem::getPrescriptionId));
        return found.stream()
            .map(p -> new PrescriptionHistoryEntry(p.getEncounterId(), p.getIssuedDate(),
                byPrescription.getOrDefault(p.getId(), List.of()).stream()
                    .map(i -> new PrescriptionHistoryEntry.Item(PrescriptionItemNames.of(i, drugCatalogRepository), i.getDosage(), i.getFrequency(), i.getDurationDays(), i.getRoute()))
                    .toList()))
            .toList();
    }

    private String buildVitalsSummary(Encounter e) {
        List<String> parts = new java.util.ArrayList<>();
        if (e.getWeightKg() != null) parts.add("Kilo: " + e.getWeightKg() + " kg");
        if (e.getTemperatureC() != null) parts.add("Ateş: " + e.getTemperatureC() + " C");
        if (e.getHeartRate() != null) parts.add("Nabız: " + e.getHeartRate() + " /dk");
        if (e.getRespiratoryRate() != null) parts.add("Solunum: " + e.getRespiratoryRate() + " /dk");
        return String.join(", ", parts);
    }

    private String buildPhysicalExamSummary(Encounter e) {
        List<PhysicalExamFinding> findings = e.getPhysicalExamFindings();
        if (findings == null || findings.isEmpty()) return "";
        return findings.stream()
            .filter(f -> f.status() != ExamFindingStatus.NOT_EXAMINED)
            .map(f -> f.system() + ": " + f.status() + (f.note() != null && !f.note().isBlank() ? " (" + f.note() + ")" : ""))
            .collect(Collectors.joining("; "));
    }
}
