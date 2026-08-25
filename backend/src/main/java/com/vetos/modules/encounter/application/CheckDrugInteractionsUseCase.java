package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.DrugInteractionWarning;
import com.vetos.modules.encounter.domain.DrugCatalog;
import com.vetos.modules.encounter.domain.DrugCatalogRepository;
import com.vetos.modules.encounter.domain.DrugInteractionEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckDrugInteractionsUseCase {

    private final DrugCatalogRepository drugCatalogRepository;

    @Transactional(readOnly = true)
    public List<DrugInteractionWarning> execute(List<UUID> drugIds) {
        List<DrugCatalog> drugs = drugIds.stream()
            .distinct()
            .map(id -> drugCatalogRepository.findById(id).orElse(null))
            .filter(Objects::nonNull)
            .toList();

        return DrugInteractionEvaluator.evaluate(drugs).stream()
            .map(w -> new DrugInteractionWarning(w.drugA().getId(), w.drugA().getName(), w.drugB().getId(), w.drugB().getName()))
            .toList();
    }
}
