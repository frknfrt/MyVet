package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.EncounterDetail;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.EncounterStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** Muayeneler sayfasi: klinikteki acik (taslak) muayeneler, unutulanlar ustte gorunsun diye en eskisi once. */
@Service
@RequiredArgsConstructor
public class ListOpenEncountersUseCase {

    private final EncounterRepository encounterRepository;
    private final GetEncounterUseCase getEncounterUseCase;

    @Transactional(readOnly = true)
    public List<EncounterDetail> execute() {
        return encounterRepository.findByStatus(EncounterStatus.DRAFT).stream()
            .map(getEncounterUseCase::toDetail)
            .sorted(Comparator.comparing(EncounterDetail::encounterDate))
            .toList();
    }
}
