package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.EncounterDetail;
import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.EncounterStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListOpenEncountersUseCaseTest {

    private final EncounterRepository repository = mock(EncounterRepository.class);
    private final GetEncounterUseCase getEncounter = mock(GetEncounterUseCase.class);

    private static EncounterDetail detail(String name, Instant date) {
        return new EncounterDetail(UUID.randomUUID(), UUID.randomUUID(), name, UUID.randomUUID(), "Dr", null, date,
            null, null, null, null, null, null, null, null, null, List.of(), EncounterStatus.DRAFT, false, null);
    }

    @Test
    void should_listDrafts_oldestFirst() {
        Encounter a = mock(Encounter.class);
        Encounter b = mock(Encounter.class);
        when(repository.findByStatus(EncounterStatus.DRAFT)).thenReturn(List.of(a, b));
        when(getEncounter.toDetail(a)).thenReturn(detail("Yeni", Instant.parse("2026-11-05T09:00:00Z")));
        when(getEncounter.toDetail(b)).thenReturn(detail("Eski", Instant.parse("2026-10-07T09:00:00Z")));

        List<EncounterDetail> result = new ListOpenEncountersUseCase(repository, getEncounter).execute();

        assertThat(result).extracting(EncounterDetail::patientName).containsExactly("Eski", "Yeni");
    }

    @Test
    void should_returnEmpty_whenNoDrafts() {
        when(repository.findByStatus(any())).thenReturn(List.of());
        assertThat(new ListOpenEncountersUseCase(repository, getEncounter).execute()).isEmpty();
    }
}
