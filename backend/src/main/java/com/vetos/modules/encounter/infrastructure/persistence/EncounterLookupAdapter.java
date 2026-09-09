package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.*;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class EncounterLookupAdapter implements EncounterLookupPort {

    private final EncounterJpaRepository jpaRepository;

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
            .sorted(Comparator.comparing(Encounter::getEncounterDate).reversed())
            .limit(historyLimit)
            .map(e -> new PastEncounterSummary(e.getEncounterDate(), e.getAssessment(), e.getPlan()))
            .toList();

        return new EncounterClinicalContext(
            current.getId(), current.getPatientId(), current.getAssessment(),
            current.getSubjective(), current.getObjective(),
            buildVitalsSummary(current), buildPhysicalExamSummary(current),
            history
        );
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
