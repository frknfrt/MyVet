package com.vetos.modules.encounter.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DrugCatalogRepository {
    DrugCatalog save(DrugCatalog drug);
    Optional<DrugCatalog> findById(UUID id);
    List<DrugCatalog> findAll();
}
