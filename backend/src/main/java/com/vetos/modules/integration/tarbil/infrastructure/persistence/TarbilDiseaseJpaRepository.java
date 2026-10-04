package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilDisease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface TarbilDiseaseJpaRepository extends JpaRepository<TarbilDisease, UUID> {
    List<TarbilDisease> findAllByOrderBySortOrderAsc();
}
