package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

interface PrescriptionItemJpaRepository extends JpaRepository<PrescriptionItem, UUID> {
    List<PrescriptionItem> findByPrescriptionId(UUID prescriptionId);
    List<PrescriptionItem> findByPrescriptionIdIn(Collection<UUID> prescriptionIds);
}
