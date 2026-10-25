package com.vetos.modules.lab.infrastructure.persistence;

import com.vetos.modules.lab.domain.LabResultItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

interface LabResultItemJpaRepository extends JpaRepository<LabResultItem, UUID> {
    List<LabResultItem> findByLabResultId(UUID labResultId);
    List<LabResultItem> findByLabResultIdIn(Collection<UUID> labResultIds);
    void deleteByLabResultId(UUID labResultId);
}
