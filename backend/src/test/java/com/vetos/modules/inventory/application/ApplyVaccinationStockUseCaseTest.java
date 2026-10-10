package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.PendingDeductionLine;
import com.vetos.modules.inventory.domain.PendingDeductionStatus;
import com.vetos.modules.inventory.domain.StockMovement;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Stoktan asi (spec 2026-10-09 S5.2): kayitta TARBIL dusumunu bekler, iptalde bekleyen kapanir; eski kayitlarda 1 IN. */
@ExtendWith(MockitoExtension.class)
class ApplyVaccinationStockUseCaseTest {

    @Mock private InventoryItemRepository items;
    @Mock private StockMovementRepository movements;
    @Mock private PendingDeductionService pending;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID vaccinationId = UUID.randomUUID();

    private ApplyVaccinationStockUseCase useCase() {
        return new ApplyVaccinationStockUseCase(items, movements, pending);
    }

    private InventoryItem item(int qty) {
        return InventoryItem.create(tenantId, UUID.randomUUID(), "Biocan R", "Aşı", null, qty, 0, null, "665932", null);
    }

    private PendingDeductionLine line(PendingDeductionStatus status) {
        return new PendingDeductionLine(UUID.randomUUID(), itemId, "Biocan R", null, "665932", 1, status,
            status == PendingDeductionStatus.DEDUCTED ? 1 : null);
    }

    @Test
    void administeredVaccineWaitsForTarbilInsteadOfDeducting() {
        InventoryItem item = item(18);
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(false);

        useCase().administered(vaccinationId, itemId, "665932");

        verify(pending).open(itemId, 1, StockReferenceType.VACCINATION, vaccinationId);
        verify(movements, never()).save(any());
        assertThat(item.getQuantityOnHand()).isEqualTo(18);
    }

    @Test
    void administeredVaccineWaitsEvenWhenStockIsEmpty() {
        InventoryItem item = item(0);
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(false);

        useCase().administered(vaccinationId, itemId, "665932");

        verify(pending).open(itemId, 1, StockReferenceType.VACCINATION, vaccinationId);
    }

    @Test
    void legacyDeductedVaccinationIsNotParkedAgain() {
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(true);

        useCase().administered(vaccinationId, itemId, "665932");

        verify(items, never()).findById(any());
        verify(pending, never()).open(any(), anyInt(), any(), any());
    }

    @Test
    void should_ignore_when_itemNotFound() {
        when(items.findById(itemId)).thenReturn(Optional.empty());
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(false);

        useCase().administered(vaccinationId, itemId, "665932");

        verify(pending, never()).open(any(), anyInt(), any(), any());
    }

    @Test
    void should_ignore_when_noItem() {
        useCase().administered(vaccinationId, null, null);
        useCase().cancelled(vaccinationId, null);

        verify(items, never()).findById(any());
        verify(movements, never()).save(any());
        verify(pending, never()).open(any(), anyInt(), any(), any());
    }

    @Test
    void cancellationBeforeTarbilCancelsThePendingDeduction() {
        when(pending.lines(StockReferenceType.VACCINATION, vaccinationId)).thenReturn(List.of(line(PendingDeductionStatus.PENDING)));

        useCase().cancelled(vaccinationId, itemId);

        verify(pending).cancel(StockReferenceType.VACCINATION, vaccinationId);
        verify(movements, never()).save(any());
    }

    @Test
    void cancellationAfterTarbilDeductionDoesNotReturnStock() {
        when(pending.lines(StockReferenceType.VACCINATION, vaccinationId)).thenReturn(List.of(line(PendingDeductionStatus.DEDUCTED)));

        useCase().cancelled(vaccinationId, itemId);

        verify(movements, never()).save(any());
        verify(items, never()).findById(any());
    }

    @Test
    void legacyDeductedVaccinationIsStillReturnedOnCancel() {
        InventoryItem item = item(17);
        when(pending.lines(StockReferenceType.VACCINATION, vaccinationId)).thenReturn(List.of());
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
    void should_returnOnlyOnce_when_legacyCancelledTwice() {
        when(pending.lines(StockReferenceType.VACCINATION, vaccinationId)).thenReturn(List.of());
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(true);
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.IN)).thenReturn(true);

        useCase().cancelled(vaccinationId, itemId);

        verify(movements, never()).save(any());
    }

    @Test
    void should_skip_when_itemIsNotAVaccine() {
        InventoryItem drug = InventoryItem.create(tenantId, UUID.randomUUID(), "Drontal", "İlaç", null, 5, 0, null, "665932", null);
        when(items.findById(itemId)).thenReturn(Optional.of(drug));
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(false);

        useCase().administered(vaccinationId, itemId, "665932");

        verify(pending, never()).open(any(), anyInt(), any(), any());
    }

    @Test
    void should_skip_when_lotDiffersFromVaccinationRecord() {
        when(items.findById(itemId)).thenReturn(Optional.of(item(18)));
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(false);

        useCase().administered(vaccinationId, itemId, "999999");

        verify(pending, never()).open(any(), anyInt(), any(), any());
    }

    @Test
    void should_acceptTarbilVaccineWithOtherCategoryAndSpacedLot() {
        InventoryItem item = InventoryItem.create(tenantId, UUID.randomUUID(), "Biocan R", "Diğer", null, 3, 0, null, "665932", null);
        item.linkTarbil("HBSAPP_VACCINE", "Biocan R", null);
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(false);

        useCase().administered(vaccinationId, itemId, " 665 932 ");

        verify(pending).open(itemId, 1, StockReferenceType.VACCINATION, vaccinationId);
    }
}
