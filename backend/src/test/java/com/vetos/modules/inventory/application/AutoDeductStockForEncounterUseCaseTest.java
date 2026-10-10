package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoDeductStockForEncounterUseCaseTest {

    @Mock private RecordStockMovementUseCase movements;
    @Mock private PendingDeductionService pending;
    @Mock private InventoryItemRepository items;

    private UUID item(boolean linked) {
        InventoryItem i = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID(), "X", null, null, 5, 0, null, "L1", null);
        ReflectionTestUtils.setField(i, "id", UUID.randomUUID()); // id @GeneratedValue
        if (linked) i.linkTarbil("VETILAC_MEDICINE", "X", "Kutu");
        when(items.findById(i.getId())).thenReturn(Optional.of(i));
        return i.getId();
    }

    @Test
    void deductsUnlinkedItemsNowAndParksTarbilLinkedOnes() {
        UUID encounter = UUID.randomUUID();
        UUID plain = item(false);
        UUID linked = item(true);

        new AutoDeductStockForEncounterUseCase(movements, pending, items).execute(encounter, List.of(
            new AutoDeductStockForEncounterUseCase.UsedItem(plain, 2),
            new AutoDeductStockForEncounterUseCase.UsedItem(linked, 1)));

        verify(movements).execute(plain, StockMovementType.OUT, 2, StockReferenceType.ENCOUNTER, encounter);
        verify(movements, never()).execute(eq(linked), any(), anyInt(), any(), any());
        verify(pending).open(linked, 1, StockReferenceType.ENCOUNTER, encounter);
    }
}
