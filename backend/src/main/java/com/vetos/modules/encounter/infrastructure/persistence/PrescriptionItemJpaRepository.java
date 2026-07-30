package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface PrescriptionItemJpaRepository extends JpaRepository<PrescriptionItem, UUID> {
    List<PrescriptionItem> findByPrescriptionId(UUID prescriptionId);
}
