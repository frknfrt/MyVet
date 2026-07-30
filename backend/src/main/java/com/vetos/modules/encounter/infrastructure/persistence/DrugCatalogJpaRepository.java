package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.DrugCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface DrugCatalogJpaRepository extends JpaRepository<DrugCatalog, UUID> {
}
