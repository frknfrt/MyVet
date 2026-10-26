package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.application.dto.UpdateInventoryItemCommand;
import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.StockUnit;
import com.vetos.modules.inventory.domain.exception.InvalidStockUnitException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Stok ekraninda elle birim ayari (spec 2026-10-24 §3.4). */
@ExtendWith(MockitoExtension.class)
class UpdateInventoryItemUnitTest {

    @Mock private InventoryItemRepository repository;
    @Mock private com.vetos.modules.inventory.domain.PendingStockDeductionRepository pending;

    private final UUID id = UUID.randomUUID();
    private final InventoryItem item = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID(), "Clavon", "İlaç", null, 1, 0, null, "63016", null);

    private void update(String unit, String packageSize) {
        when(repository.findById(id)).thenReturn(Optional.of(item));
        new UpdateInventoryItemUseCase(repository, pending).execute(new UpdateInventoryItemCommand(id, "Clavon", "İlaç", 0, null,
            unit, packageSize == null ? null : new BigDecimal(packageSize), null, null));
    }

    @Test
    void manualUnit_isSavedAsManual() {
        update("ML", "50");
        assertThat(item.getUnit()).isEqualTo(StockUnit.ML);
        assertThat(item.getPackageSize()).isEqualByComparingTo("50");
        assertThat(item.isUnitManual()).isTrue();
    }

    @Test
    void nullUnit_leavesUnitAlone() {
        update(null, null);
        assertThat(item.getUnit()).isEqualTo(StockUnit.ADET);
        assertThat(item.isUnitManual()).isFalse();
    }

    @Test
    void packagedUnitWithoutSize_isRejected() {
        assertThatThrownBy(() -> update("ML", null)).isInstanceOf(InvalidStockUnitException.class);
    }

    @Test
    void unknownUnit_isRejected() {
        assertThatThrownBy(() -> update("LITRE", "1")).isInstanceOf(InvalidStockUnitException.class);
    }

    @Test
    void unitChangeWhileDeductionsArePending_isRejected() {
        when(pending.existsPendingForItem(id)).thenReturn(true);
        assertThatThrownBy(() -> update("ML", "50"))
            .isInstanceOf(com.vetos.modules.inventory.domain.exception.StockUnitPendingConflictException.class);
        assertThat(item.getUnit()).isEqualTo(StockUnit.ADET);
    }

    @Test
    void savingTheSameUnitWhileDeductionsArePending_isAllowed() {
        item.setStockUnit(StockUnit.ML, new BigDecimal("50"), false);
        update("ML", "50.00");
        assertThat(item.isUnitManual()).isTrue();
    }

    @Test
    void update_rejectsFractionalTabletPackage() {
        assertThatThrownBy(() -> update("TABLET", "10.5")).isInstanceOf(InvalidStockUnitException.class);
    }

    @Test
    void update_savesPieceForMl() {
        when(repository.findById(id)).thenReturn(Optional.of(item));
        new UpdateInventoryItemUseCase(repository, pending).execute(new UpdateInventoryItemCommand(id, "Fiproes", "İlaç", 0, null,
            "ML", new BigDecimal("1.5"), new BigDecimal("0.5"), "pipet"));
        assertThat(item.getPieceSize()).isEqualByComparingTo("0.5");
        assertThat(item.getPieceLabel()).isEqualTo("pipet");
    }
}
