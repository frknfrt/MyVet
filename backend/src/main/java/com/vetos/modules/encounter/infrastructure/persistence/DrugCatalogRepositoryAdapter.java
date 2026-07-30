package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.DrugCatalog;
import com.vetos.modules.encounter.domain.DrugCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class DrugCatalogRepositoryAdapter implements DrugCatalogRepository {

    private final DrugCatalogJpaRepository jpaRepository;

    @Override
    public DrugCatalog save(DrugCatalog drug) { return jpaRepository.save(drug); }

    @Override
    public Optional<DrugCatalog> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<DrugCatalog> findAll() { return jpaRepository.findAll(); }
}
