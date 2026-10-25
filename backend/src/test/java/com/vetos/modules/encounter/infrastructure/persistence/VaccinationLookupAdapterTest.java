package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaccinationLookupAdapterTest {

    @Mock private VaccinationRecordJpaRepository jpaRepository;

    @Test
    void should_mapAllFields_when_findForTarbil() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        VaccinationRecord record = mock(VaccinationRecord.class);
        when(record.getId()).thenReturn(id);
        when(record.getTenantId()).thenReturn(tenantId);
        when(record.getPatientId()).thenReturn(patientId);
        when(record.getVaccineName()).thenReturn("Kuduz");
        when(record.getLotNumber()).thenReturn("L-42");
        when(record.getAdministeredDate()).thenReturn(LocalDate.of(2026, 10, 1));
        when(record.getStatus()).thenReturn(VaccinationStatus.ADMINISTERED);
        when(jpaRepository.findById(id)).thenReturn(Optional.of(record));

        Optional<VaccinationTarbilView> view = new VaccinationLookupAdapter(jpaRepository).findForTarbil(id);

        assertThat(view).contains(new VaccinationTarbilView(
            id, tenantId, patientId, "Kuduz", "L-42", LocalDate.of(2026, 10, 1), VaccinationStatus.ADMINISTERED));
    }

    private VaccinationRecord vaccine(String name, LocalDate date, VaccinationStatus status) {
        VaccinationRecord r = mock(VaccinationRecord.class);
        org.mockito.Mockito.lenient().when(r.getVaccineName()).thenReturn(name);
        org.mockito.Mockito.lenient().when(r.getAdministeredDate()).thenReturn(date);
        org.mockito.Mockito.lenient().when(r.getStatus()).thenReturn(status);
        org.mockito.Mockito.lenient().when(r.getNextDueDate()).thenReturn(date == null ? null : date.plusYears(1));
        return r;
    }

    @Test
    void findAdministeredBetween_mapsTheBoundedQueryResult() {
        UUID patientId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2025, 10, 20);
        LocalDate to = LocalDate.of(2026, 10, 20);
        var records = java.util.List.of(
            vaccine("Karma", from.plusDays(30), VaccinationStatus.ADMINISTERED),
            vaccine("Kuduz", from.plusDays(2), VaccinationStatus.ADMINISTERED));
        when(jpaRepository.findByPatientIdAndStatusAndAdministeredDateBetweenOrderByAdministeredDateDesc(
            patientId, VaccinationStatus.ADMINISTERED, from, to, org.springframework.data.domain.PageRequest.of(0, 21))).thenReturn(records);

        var history = new VaccinationLookupAdapter(jpaRepository).findAdministeredBetween(patientId, from, to, 21);

        assertThat(history).containsExactly(
            new com.vetos.modules.encounter.domain.VaccinationHistoryEntry("Karma", from.plusDays(30), from.plusDays(30).plusYears(1)),
            new com.vetos.modules.encounter.domain.VaccinationHistoryEntry("Kuduz", from.plusDays(2), from.plusDays(2).plusYears(1)));
    }

    @Test
    void should_returnEmpty_when_recordMissing() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(new VaccinationLookupAdapter(jpaRepository).findForTarbil(id)).isEmpty();
    }
}
