package com.vetos.modules.inventory.infrastructure.persistence;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class InventoryItemRepositoryAdapter implements InventoryItemRepository {

    private final InventoryItemJpaRepository jpaRepository;

    @Override
    public InventoryItem save(InventoryItem item) { return jpaRepository.save(item); }

    @Override
    public Optional<InventoryItem> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<InventoryItem> findByBranchId(UUID branchId) { return jpaRepository.findByBranchId(branchId); }
}
