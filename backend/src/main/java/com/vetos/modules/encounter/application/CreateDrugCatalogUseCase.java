package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.DrugCatalog;
import com.vetos.modules.encounter.domain.DrugCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateDrugCatalogUseCase {

    private final DrugCatalogRepository drugCatalogRepository;

    @Transactional
    public UUID execute(String name, String activeIngredient, boolean isControlled) {
        return drugCatalogRepository.save(DrugCatalog.create(name, activeIngredient, isControlled)).getId();
    }
}
