package com.vetos.modules.imaging.infrastructure.persistence;

import com.vetos.modules.imaging.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Süzme/sıralama/sınır kuralları gerçek veritabanında: PatientHistoryQueriesTest. Burada eşleme. */
@ExtendWith(MockitoExtension.class)
class ImagingRecordLookupAdapterTest {

    @Mock private ImagingRecordJpaRepository records;

    private static final Instant FROM = Instant.parse("2025-10-20T00:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-10-20T00:00:00Z");

    private ImagingRecord record(ImagingModality modality, Instant requestedAt, Instant resultedAt, String findings) {
        ImagingRecord r = mock(ImagingRecord.class);
        lenient().when(r.getModality()).thenReturn(modality);
        lenient().when(r.getBodyRegion()).thenReturn("Toraks");
        lenient().when(r.getRequestedAt()).thenReturn(requestedAt);
        lenient().when(r.getResultedAt()).thenReturn(resultedAt);
        lenient().when(r.getFindings()).thenReturn(findings);
        return r;
    }

    @Test
    void mapsRecords_usingResultDateOrRequestDate() {
        UUID patientId = UUID.randomUUID();
        var found = List.of(
            record(ImagingModality.XRAY, Instant.parse("2026-06-01T09:00:00Z"), Instant.parse("2026-06-01T10:00:00Z"), "kardiyomegali"),
            record(ImagingModality.ULTRASOUND, Instant.parse("2026-01-01T09:00:00Z"), null, "karaciğer normal"));
        when(records.findCompletedHistory(patientId, FROM, BEFORE, PageRequest.of(0, 11))).thenReturn(found);

        assertThat(new ImagingRecordLookupAdapter(records).findCompletedBetween(patientId, FROM, BEFORE, 11)).containsExactly(
            new ImagingHistoryEntry(ImagingModality.XRAY, "Toraks", Instant.parse("2026-06-01T10:00:00Z"), "kardiyomegali"),
            new ImagingHistoryEntry(ImagingModality.ULTRASOUND, "Toraks", Instant.parse("2026-01-01T09:00:00Z"), "karaciğer normal"));
    }
}
