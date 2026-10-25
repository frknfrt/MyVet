package com.vetos.modules.lab.infrastructure.persistence;

import com.vetos.modules.lab.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/** Süzme/sıralama/sınır kuralları gerçek veritabanında: PatientHistoryQueriesTest. Burada eşleme ve toplu okuma. */
@ExtendWith(MockitoExtension.class)
class LabResultLookupAdapterTest {

    @Mock private LabResultJpaRepository results;
    @Mock private LabResultItemJpaRepository items;

    private static final Instant FROM = Instant.parse("2025-10-20T00:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-10-20T00:00:00Z");

    private LabResult result(String name, Instant requestedAt, Instant resultedAt) {
        LabResult r = mock(LabResult.class);
        lenient().when(r.getId()).thenReturn(UUID.randomUUID());
        lenient().when(r.getTestName()).thenReturn(name);
        lenient().when(r.getRequestedAt()).thenReturn(requestedAt);
        lenient().when(r.getResultedAt()).thenReturn(resultedAt);
        lenient().when(r.getResultSummary()).thenReturn("özet " + name);
        return r;
    }

    @Test
    void mapsResultsWithTheirValues_readingAllValuesInOneQuery() {
        UUID patientId = UUID.randomUUID();
        LabResult bio = result("Biyokimya", Instant.parse("2026-03-01T09:00:00Z"), Instant.parse("2026-03-02T09:00:00Z"));
        LabResult hemogram = result("Hemogram", Instant.parse("2026-01-01T09:00:00Z"), Instant.parse("2026-01-01T12:00:00Z"));
        LabResultItem bun = mock(LabResultItem.class);
        UUID bioId = bio.getId();
        when(bun.getLabResultId()).thenReturn(bioId);
        when(bun.getParameterName()).thenReturn("BUN");
        when(bun.getValue()).thenReturn("45");
        when(bun.getUnit()).thenReturn("mg/dL");
        when(bun.getReferenceRange()).thenReturn("7-27");
        when(bun.getFlag()).thenReturn(LabValueFlag.HIGH);
        var found = List.of(bio, hemogram);
        when(results.findCompletedHistory(patientId, FROM, BEFORE, PageRequest.of(0, 16))).thenReturn(found);
        when(items.findByLabResultIdIn(List.of(bio.getId(), hemogram.getId()))).thenReturn(List.of(bun));

        List<LabResultHistoryEntry> history = new LabResultLookupAdapter(results, items).findCompletedBetween(patientId, FROM, BEFORE, 16);

        assertThat(history).extracting(LabResultHistoryEntry::testName).containsExactly("Biyokimya", "Hemogram");
        assertThat(history.get(0).date()).isEqualTo(Instant.parse("2026-03-02T09:00:00Z"));
        assertThat(history.get(0).resultSummary()).isEqualTo("özet Biyokimya");
        assertThat(history.get(0).values()).containsExactly(new LabResultHistoryEntry.Value("BUN", "45", "mg/dL", "7-27", LabValueFlag.HIGH));
        assertThat(history.get(1).values()).isEmpty();
        verify(items, times(1)).findByLabResultIdIn(anyCollection());
    }

    @Test
    void usesRequestedAtWhenResultedAtMissing() {
        UUID patientId = UUID.randomUUID();
        Instant requested = Instant.parse("2026-02-01T09:00:00Z");
        var found = List.of(result("Hemogram", requested, null));
        when(results.findCompletedHistory(patientId, FROM, BEFORE, PageRequest.of(0, 5))).thenReturn(found);
        when(items.findByLabResultIdIn(anyCollection())).thenReturn(List.of());

        assertThat(new LabResultLookupAdapter(results, items).findCompletedBetween(patientId, FROM, BEFORE, 5))
            .singleElement().extracting(LabResultHistoryEntry::date).isEqualTo(requested);
    }

    @Test
    void skipsTheValuesQuery_whenNothingFound() {
        UUID patientId = UUID.randomUUID();
        when(results.findCompletedHistory(patientId, FROM, BEFORE, PageRequest.of(0, 5))).thenReturn(List.of());

        assertThat(new LabResultLookupAdapter(results, items).findCompletedBetween(patientId, FROM, BEFORE, 5)).isEmpty();
        verifyNoInteractions(items);
    }
}
