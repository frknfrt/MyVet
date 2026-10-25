package com.vetos.modules.imaging.infrastructure.persistence;

import com.vetos.modules.imaging.domain.ImagingHistoryEntry;
import com.vetos.modules.imaging.domain.ImagingRecordLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Tarih aralığı ve üst sınır veritabanında (2026-10-25). */
@Component
@RequiredArgsConstructor
class ImagingRecordLookupAdapter implements ImagingRecordLookupPort {

    private final ImagingRecordJpaRepository records;

    @Override
    public List<ImagingHistoryEntry> findCompletedBetween(UUID patientId, Instant from, Instant requestedBefore, int limit) {
        return records.findCompletedHistory(patientId, from, requestedBefore, PageRequest.of(0, limit)).stream()
            .map(r -> new ImagingHistoryEntry(r.getModality(), r.getBodyRegion(),
                r.getResultedAt() != null ? r.getResultedAt() : r.getRequestedAt(), r.getFindings()))
            .toList();
    }
}
