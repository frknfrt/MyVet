package com.vetos.modules.lab.infrastructure.persistence;

import com.vetos.modules.lab.domain.LabResultItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface LabResultItemJpaRepository extends JpaRepository<LabResultItem, UUID> {
    List<LabResultItem> findByLabResultId(UUID labResultId);
    void deleteByLabResultId(UUID labResultId);
}
