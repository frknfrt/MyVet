package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.PrescriptionItem;
import com.vetos.modules.encounter.domain.PrescriptionItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PrescriptionItemRepositoryAdapter implements PrescriptionItemRepository {

    private final PrescriptionItemJpaRepository jpaRepository;

    @Override
    public PrescriptionItem save(PrescriptionItem item) { return jpaRepository.save(item); }

    @Override
    public List<PrescriptionItem> findByPrescriptionId(UUID prescriptionId) { return jpaRepository.findByPrescriptionId(prescriptionId); }
}
