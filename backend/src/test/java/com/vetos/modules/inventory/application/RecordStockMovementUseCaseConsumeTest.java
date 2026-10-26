package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.StockMovement;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.StockSource;
import com.vetos.modules.inventory.domain.StockUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kullanim dusumu stok hareketine yazilir: kapali fark quantity'de, acik fark openedDelta'da (spec 2026-10-24). */
@ExtendWith(MockitoExtension.class)
class RecordStockMovementUseCaseConsumeTest {

    @Mock private InventoryItemRepository items;
    @Mock private StockMovementRepository movements;

    private final UUID id = UUID.randomUUID();
    private final UUID ref = UUID.randomUUID();

    private InventoryItem clavon(int closed, String opened) {
        InventoryItem i = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID(), "Clavon", "İlaç", null, closed, 0, null, "63016", null);
        i.setStockUnit(StockUnit.ML, new BigDecimal("50"), false);
        i.syncTo(closed, new BigDecimal(opened));
        when(items.findByIdForUpdate(id)).thenReturn(Optional.of(i));
        org.mockito.Mockito.lenient().when(movements.save(any())).thenAnswer(a -> a.getArgument(0));
        return i;
    }

    private StockMovement saved() {
        ArgumentCaptor<StockMovement> m = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements).save(m.capture());
        return m.getValue();
    }

    @Test
    void fromOpen_deductsOpenOnly() {
        InventoryItem i = clavon(1, "49");
        new RecordStockMovementUseCase(items, movements).consume(id, new BigDecimal("1"), StockSource.OPEN, StockReferenceType.PRESCRIPTION, ref);

        assertThat(i.getQuantityOnHand()).isEqualTo(1);
        assertThat(i.getOpenedQuantity()).isEqualByComparingTo("48");
        StockMovement m = saved();
        assertThat(m.getMovementType()).isEqualTo(StockMovementType.OUT);
        assertThat(m.getQuantity()).isZero();
        assertThat(m.getOpenedDelta()).isEqualByComparingTo("-1");
        verify(items).save(i);
    }

    @Test
    void whole_opensBottle_andRecordsBothDeltas() {
        InventoryItem i = clavon(1, "0");
        new RecordStockMovementUseCase(items, movements).consume(id, new BigDecimal("1"), StockSource.WHOLE, StockReferenceType.PRESCRIPTION, ref);

        assertThat(i.getQuantityOnHand()).isZero();
        assertThat(i.getOpenedQuantity()).isEqualByComparingTo("49");
        StockMovement m = saved();
        assertThat(m.getQuantity()).isEqualTo(1);
        assertThat(m.getOpenedDelta()).isEqualByComparingTo("49");
    }

    @Test
    void adjustOpened_writesAdjustmentMovementWithOpenedDelta() {
        InventoryItem i = clavon(1, "49");
        new RecordStockMovementUseCase(items, movements).adjustOpened(id, new BigDecimal("-3"));

        assertThat(i.getOpenedQuantity()).isEqualByComparingTo("46");
        StockMovement m = saved();
        assertThat(m.getMovementType()).isEqualTo(StockMovementType.ADJUSTMENT);
        assertThat(m.getQuantity()).isZero();
        assertThat(m.getOpenedDelta()).isEqualByComparingTo("-3");
        assertThat(m.getReferenceType()).isEqualTo(StockReferenceType.MANUAL);
    }

    @Test
    void manual_outPlusOpenedBelowZero_changesNothing() {
        InventoryItem i = clavon(1, "49");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RecordStockMovementUseCase(items, movements)
                .recordManual(id, StockMovementType.OUT, 1, new BigDecimal("-60")))
            .isInstanceOf(com.vetos.modules.inventory.domain.exception.OpenedQuantityNegativeException.class);
        assertThat(i.getQuantityOnHand()).isEqualTo(1);
        assertThat(i.getOpenedQuantity()).isEqualByComparingTo("49");
        org.mockito.Mockito.verify(movements, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void manual_closedAndOpenedTogether_writesBothMovements() {
        InventoryItem i = clavon(2, "49");
        new RecordStockMovementUseCase(items, movements).recordManual(id, StockMovementType.OUT, 1, new BigDecimal("-3"));
        assertThat(i.getQuantityOnHand()).isEqualTo(1);
        assertThat(i.getOpenedQuantity()).isEqualByComparingTo("46");
        org.mockito.Mockito.verify(movements, org.mockito.Mockito.times(2)).save(any());
    }
}
