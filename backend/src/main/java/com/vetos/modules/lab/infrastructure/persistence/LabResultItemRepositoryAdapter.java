package com.vetos.modules.lab.infrastructure.persistence;

import com.vetos.modules.lab.domain.LabResultItem;
import com.vetos.modules.lab.domain.LabResultItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class LabResultItemRepositoryAdapter implements LabResultItemRepository {

    private final LabResultItemJpaRepository jpaRepository;

    @Override
    public LabResultItem save(LabResultItem item) { return jpaRepository.save(item); }

    @Override
    public List<LabResultItem> findByLabResultId(UUID labResultId) { return jpaRepository.findByLabResultId(labResultId); }

    @Override
    public void deleteByLabResultId(UUID labResultId) { jpaRepository.deleteByLabResultId(labResultId); }
}
