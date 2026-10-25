package com.vetos.modules.lab.infrastructure.persistence;

import com.vetos.modules.lab.domain.LabResult;
import com.vetos.modules.lab.domain.LabResultHistoryEntry;
import com.vetos.modules.lab.domain.LabResultItem;
import com.vetos.modules.lab.domain.LabResultLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Tarih aralığı ve üst sınır veritabanında; değerler tek sorguda toplu okunur (2026-10-25). */
@Component
@RequiredArgsConstructor
class LabResultLookupAdapter implements LabResultLookupPort {

    private final LabResultJpaRepository results;
    private final LabResultItemJpaRepository items;

    @Override
    public List<LabResultHistoryEntry> findCompletedBetween(UUID patientId, Instant from, Instant requestedBefore, int limit) {
        List<LabResult> found = results.findCompletedHistory(patientId, from, requestedBefore, PageRequest.of(0, limit));
        if (found.isEmpty()) return List.of();
        Map<UUID, List<LabResultItem>> byResult = items.findByLabResultIdIn(found.stream().map(LabResult::getId).toList()).stream()
            .collect(Collectors.groupingBy(LabResultItem::getLabResultId));
        return found.stream()
            .map(r -> new LabResultHistoryEntry(r.getTestName(), r.getResultedAt() != null ? r.getResultedAt() : r.getRequestedAt(),
                r.getResultSummary(), byResult.getOrDefault(r.getId(), List.of()).stream()
                    .map(i -> new LabResultHistoryEntry.Value(i.getParameterName(), i.getValue(), i.getUnit(), i.getReferenceRange(), i.getFlag()))
                    .toList()))
            .toList();
    }
}
