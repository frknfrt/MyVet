package com.vetos;

import com.vetos.modules.inventory.application.RecordStockMovementUseCase;
import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.StockUnit;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ayni kaleme es zamanli iki dusum birbirini ezmemeli (spec 2026-10-25 §4.3/8). Birinci islem satiri kilitleyip tutarken
 * ikincisi dusmeye calisir; kilit yoksa ikincisi 49'dan hesaplar ve bir dusum kaybolur. Canli docker-compose Postgres gerekir.
 */
@SpringBootTest
class StockLockingTest extends TenantScopedTestSupport {

    @Autowired TenantRepository tenantRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired InventoryItemRepository inventoryItemRepository;
    @Autowired RecordStockMovementUseCase useCase;
    @PersistenceContext EntityManager entityManager;

    private UUID tenantId;

    @AfterEach
    void purge() {
        if (tenantId == null) return;
        inRootSessionVoid(() -> List.of(
            "DELETE FROM stock_movements WHERE tenant_id = :t",
            "DELETE FROM inventory_items WHERE tenant_id = :t",
            "DELETE FROM subscriptions WHERE tenant_id = :t",
            "DELETE FROM branches WHERE tenant_id = :t",
            "DELETE FROM tenants WHERE id = :t"
        ).forEach(sql -> entityManager.createNativeQuery(sql).setParameter("t", tenantId).executeUpdate()));
    }

    @Test
    void concurrentConsumesOfTheSameItem_bothCount() throws Exception {
        tenantId = inRootSession(() -> tenantRepository.save(Tenant.register("Kilit " + UUID.randomUUID(), null)).getId());
        UUID branchId = inRootSession(() -> branchRepository.save(Branch.create(tenantId, "Merkez")).getId());
        UUID itemId = asTenant(tenantId, () -> {
            InventoryItem i = InventoryItem.create(tenantId, branchId, "Clavon", "İlaç", null, 2, 0, null, "63016", null);
            i.setStockUnit(StockUnit.ML, new BigDecimal("50"), false);
            i.syncTo(2, new BigDecimal("49"));
            return inventoryItemRepository.save(i).getId();
        });
        CountDownLatch firstHoldsRow = new CountDownLatch(1);

        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> asTenantVoid(tenantId, () -> {
            useCase.consume(itemId, new BigDecimal("10"), null, StockReferenceType.PRESCRIPTION, UUID.randomUUID());
            firstHoldsRow.countDown();
            sleep(800);
        }));
        assertThat(firstHoldsRow.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> asTenantVoid(tenantId, () ->
            useCase.consume(itemId, new BigDecimal("10"), null, StockReferenceType.PRESCRIPTION, UUID.randomUUID())));
        CompletableFuture.allOf(first, second).get(20, TimeUnit.SECONDS);

        InventoryItem after = asTenant(tenantId, () -> inventoryItemRepository.findById(itemId).orElseThrow());
        assertThat(after.getOpenedQuantity()).isEqualByComparingTo("29");
        assertThat(after.getQuantityOnHand()).isEqualTo(2);
    }

    @Test
    void twoConsumesInOneTransaction_bothCount() {
        tenantId = inRootSession(() -> tenantRepository.save(Tenant.register("Kilit2 " + UUID.randomUUID(), null)).getId());
        UUID branchId = inRootSession(() -> branchRepository.save(Branch.create(tenantId, "Merkez")).getId());
        UUID itemId = asTenant(tenantId, () -> inventoryItemRepository.save(
            InventoryItem.create(tenantId, branchId, "Enjektör", "Sarf", null, 10, 0, null, "E1", null)).getId());

        // Muayene kapanisinda ayni kalem iki satir (degerlendirme I1): ayni islemde iki dusum.
        asTenantVoid(tenantId, () -> {
            useCase.consume(itemId, new BigDecimal("1"), null, StockReferenceType.ENCOUNTER, UUID.randomUUID());
            useCase.consume(itemId, new BigDecimal("1"), null, StockReferenceType.ENCOUNTER, UUID.randomUUID());
        });

        InventoryItem after = asTenant(tenantId, () -> inventoryItemRepository.findById(itemId).orElseThrow());
        assertThat(after.getQuantityOnHand()).isEqualTo(8);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
