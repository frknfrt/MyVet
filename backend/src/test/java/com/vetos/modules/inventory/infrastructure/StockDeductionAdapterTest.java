package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.application.RecordStockMovementUseCase;
import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

@ExtendWith(MockitoExtension.class)
class StockDeductionAdapterTest {

    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private RecordStockMovementUseCase recordStockMovementUseCase;

    private StockDeductionAdapter adapter;

    @Test
    void should_throwInsufficientStock_when_quantityExceedsOnHand() {
        adapter = new StockDeductionAdapter(inventoryItemRepository, recordStockMovementUseCase);
        UUID itemId = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(
            UUID.randomUUID(), "Mama", "Gida", null, 2, 1, null, null, BigDecimal.TEN
        );
        when(inventoryItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        // Mesaj kullaniciya gosteriliyor -- ham UUID degil, urun adi gecmeli.
        assertThatThrownBy(() -> adapter.deductForSale(itemId, 5, UUID.randomUUID()))
            .isInstanceOf(InsufficientStockException.class)
            .hasMessageContaining("Mama")
            .hasMessageNotContaining(itemId.toString());

        verify(recordStockMovementUseCase, never()).execute(any(), any(), anyInt(), any(), any());
    }

    @Test
    void should_recordOutMovement_when_stockSufficient() {
        adapter = new StockDeductionAdapter(inventoryItemRepository, recordStockMovementUseCase);
        UUID itemId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(
            UUID.randomUUID(), "Mama", "Gida", null, 10, 1, null, null, BigDecimal.TEN
        );
        when(inventoryItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        adapter.deductForSale(itemId, 3, invoiceId);

        verify(recordStockMovementUseCase).execute(
            itemId, com.vetos.modules.inventory.domain.StockMovementType.OUT, 3,
            com.vetos.modules.inventory.domain.StockReferenceType.MANUAL, invoiceId
        );
    }
}
