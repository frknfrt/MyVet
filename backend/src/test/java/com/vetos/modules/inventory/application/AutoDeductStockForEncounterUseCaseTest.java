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

import java.math.BigDecimal;
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
            new AutoDeductStockForEncounterUseCase.UsedItem(plain, new BigDecimal("2")),
            new AutoDeductStockForEncounterUseCase.UsedItem(linked, new BigDecimal("1"))));

        verify(movements).consume(plain, new BigDecimal("2"), null, StockReferenceType.ENCOUNTER, encounter);
        verify(movements, never()).consume(eq(linked), any(), any(), any(), any());
        verify(pending).open(linked, new BigDecimal("1"), StockReferenceType.ENCOUNTER, encounter);
    }

    @Test
    void unlinkedMlItem_consumesDecimalAmountImmediately() {
        UUID encounter = UUID.randomUUID();
        UUID ml = UUID.randomUUID();
        com.vetos.modules.inventory.domain.InventoryItem plainMl = com.vetos.modules.inventory.domain.InventoryItem.create(
            UUID.randomUUID(), UUID.randomUUID(), "Serum", "İlaç", null, 2, 0, null, null, null);
        plainMl.setStockUnit(com.vetos.modules.inventory.domain.StockUnit.ML, new BigDecimal("100"), false);
        org.mockito.Mockito.when(items.findById(ml)).thenReturn(java.util.Optional.of(plainMl));

        new AutoDeductStockForEncounterUseCase(movements, pending, items).execute(encounter, List.of(
            new AutoDeductStockForEncounterUseCase.UsedItem(ml, new BigDecimal("2.5"))));

        verify(movements).consume(ml, new BigDecimal("2.5"), null, StockReferenceType.ENCOUNTER, encounter);
    }

    @Test
    void fractionalAmountOnAnItemUsedByTheUnit_isRoundedUp_soFinalizingNeverFails() {
        UUID encounter = UUID.randomUUID();
        UUID plain = item(false);
        UUID linked = item(true);

        new AutoDeductStockForEncounterUseCase(movements, pending, items).execute(encounter, List.of(
            new AutoDeductStockForEncounterUseCase.UsedItem(plain, new BigDecimal("1.5")),
            new AutoDeductStockForEncounterUseCase.UsedItem(linked, new BigDecimal("0.2"))));

        verify(movements).consume(plain, new BigDecimal("2"), null, StockReferenceType.ENCOUNTER, encounter);
        verify(pending).open(linked, new BigDecimal("1"), StockReferenceType.ENCOUNTER, encounter);
    }
}
