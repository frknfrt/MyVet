package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.UpdateDrugCatalogCommand;
import com.vetos.modules.encounter.domain.DrugCatalog;
import com.vetos.modules.encounter.domain.DrugCatalogRepository;
import com.vetos.modules.encounter.domain.exception.DrugNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateDrugCatalogUseCase {

    private final DrugCatalogRepository drugCatalogRepository;

    @Transactional
    public void execute(UpdateDrugCatalogCommand command) {
        DrugCatalog drug = drugCatalogRepository.findById(command.id())
            .orElseThrow(() -> new DrugNotFoundException(command.id()));
        drug.update(command.name(), command.activeIngredient(), command.isControlled());
        drug.updateInteractingDrugIds(command.interactingDrugIds());
        drugCatalogRepository.save(drug);
    }
}
