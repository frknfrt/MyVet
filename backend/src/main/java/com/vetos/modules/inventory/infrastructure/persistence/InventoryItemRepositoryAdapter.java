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

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Override
    public InventoryItem save(InventoryItem item) { return jpaRepository.save(item); }

    @Override
    public Optional<InventoryItem> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public Optional<InventoryItem> findByIdForUpdate(UUID id) {
        // findById kiraci filtresinden gecer; refresh satiri SELECT ... FOR UPDATE ile kilitler ve oturumda onceden okunmus
        // olsa bile veritabanindaki guncel degeri yukler (es zamanli dusum, spec 2026-10-25 §4.3/8).
        Optional<InventoryItem> item = jpaRepository.findById(id);
        // refresh kaydedilmemis degisiklikleri ezer: ayni islemde onceki dusum once yazilir (degerlendirme I1).
        item.ifPresent(i -> {
            entityManager.flush();
            entityManager.refresh(i, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        });
        return item;
    }

    @Override
    public List<InventoryItem> findByBranchId(UUID branchId) { return jpaRepository.findByBranchId(branchId); }
}
