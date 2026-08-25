package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.DrugSummary;
import com.vetos.modules.encounter.domain.DrugCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListDrugCatalogUseCase {

    private final DrugCatalogRepository drugCatalogRepository;

    @Transactional(readOnly = true)
    public List<DrugSummary> execute() {
        return drugCatalogRepository.findAll().stream()
            .map(d -> new DrugSummary(d.getId(), d.getName(), d.getActiveIngredient(), d.isControlled(), d.getInteractingDrugIds()))
            .toList();
    }
}
