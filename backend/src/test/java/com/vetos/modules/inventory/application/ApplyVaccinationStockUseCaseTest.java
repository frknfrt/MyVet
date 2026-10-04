package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.StockMovement;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyVaccinationStockUseCaseTest {

    @Mock private InventoryItemRepository items;
    @Mock private StockMovementRepository movements;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID vaccinationId = UUID.randomUUID();

    private ApplyVaccinationStockUseCase useCase() {
        return new ApplyVaccinationStockUseCase(items, movements);
    }

    private InventoryItem item(int qty) {
        return InventoryItem.create(tenantId, UUID.randomUUID(), "Biocan R", "Aşı", null, qty, 0, null, "665932", null);
    }

    @Test
    void should_deductOne_when_vaccinationAdministeredFromStock() {
        InventoryItem item = item(18);
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(false);

        useCase().administered(vaccinationId, itemId);

        assertThat(item.getQuantityOnHand()).isEqualTo(17);
        ArgumentCaptor<StockMovement> m = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements).save(m.capture());
        assertThat(m.getValue().getMovementType()).isEqualTo(StockMovementType.OUT);
        assertThat(m.getValue().getQuantity()).isEqualTo(1);
        assertThat(m.getValue().getReferenceType()).isEqualTo(StockReferenceType.VACCINATION);
        assertThat(m.getValue().getReferenceId()).isEqualTo(vaccinationId);
        verify(items).save(item);
    }

    @Test
    void should_deductOnlyOnce_when_sameVaccinationReportedTwice() {
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(true);

        useCase().administered(vaccinationId, itemId);

        verify(items, never()).findById(any());
        verify(movements, never()).save(any());
    }

    @Test
    void should_notGoNegative_when_stockIsEmpty() {
        InventoryItem item = item(0);
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(false);

        useCase().administered(vaccinationId, itemId);

        assertThat(item.getQuantityOnHand()).isZero();
        verify(movements, never()).save(any());
    }

    @Test
    void should_ignore_when_itemNotFound() {
        when(items.findById(itemId)).thenReturn(Optional.empty());
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(false);

        useCase().administered(vaccinationId, itemId);

        verify(movements, never()).save(any());
    }

    @Test
    void should_ignore_when_noItem() {
        useCase().administered(vaccinationId, null);
        useCase().cancelled(vaccinationId, null);

        verify(items, never()).findById(any());
        verify(movements, never()).save(any());
    }

    @Test
    void should_returnOne_when_deductedVaccinationCancelled() {
        InventoryItem item = item(17);
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(true);
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.IN)).thenReturn(false);

        useCase().cancelled(vaccinationId, itemId);

        assertThat(item.getQuantityOnHand()).isEqualTo(18);
        ArgumentCaptor<StockMovement> m = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements).save(m.capture());
        assertThat(m.getValue().getMovementType()).isEqualTo(StockMovementType.IN);
    }

    @Test
    void should_notReturnStock_when_cancelledBeforeAnyDeduction() {
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(false);

        useCase().cancelled(vaccinationId, itemId);

        verify(items, never()).findById(any());
        verify(movements, never()).save(any());
    }

    @Test
    void should_returnOnlyOnce_when_cancelledTwice() {
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(true);
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.IN)).thenReturn(true);

        useCase().cancelled(vaccinationId, itemId);

        verify(movements, never()).save(any());
    }
}
