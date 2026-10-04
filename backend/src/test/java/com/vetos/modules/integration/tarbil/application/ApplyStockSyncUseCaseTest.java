package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilStockSnapshotNotFoundException;
import com.vetos.modules.inventory.domain.InventoryStockView;
import com.vetos.modules.inventory.domain.NewTarbilStockItem;
import com.vetos.modules.inventory.domain.TarbilStockLink;
import com.vetos.modules.inventory.domain.TarbilStockSyncPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyStockSyncUseCaseTest {

    @Mock private TarbilStockSnapshotRepository repository;
    @Mock private TarbilStockSyncPort port;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();

    private TarbilStockSnapshot snapshot(TarbilStockSystem system) {
        return TarbilStockSnapshot.take(tenantId, system, UUID.randomUUID(), Instant.now());
    }

    private TarbilStockSnapshotLine line(int no, String product, String lot, int qty) {
        return TarbilStockSnapshotLine.of(null, tenantId, no, product, "Kutu", lot, null, qty, null);
    }

    private ApplyStockSyncUseCase useCase() {
        return new ApplyStockSyncUseCase(repository, port);
    }

    @Test
    void should_createNewItemsAndSyncDifferentOnes() {
        UUID snapshotId = UUID.randomUUID();
        TarbilStockSnapshot s = snapshot(TarbilStockSystem.VETILAC_MEDICINE);
        TarbilStockSnapshotLine fresh = line(1, "Drontal", "L1", 3);
        TarbilStockSnapshotLine differs = line(2, "Rabisin", "R9", 10);
        UUID existing = UUID.randomUUID();
        when(repository.findByIdForUpdate(snapshotId)).thenReturn(Optional.of(s));
        when(repository.findLines(snapshotId)).thenReturn(List.of(fresh, differs));
        when(port.listForBranch(branchId)).thenReturn(List.of(new InventoryStockView(existing, "Rabisin", "R9", null, 4, null)));
        UUID created = UUID.randomUUID();
        when(port.createFromTarbil(eq(branchId), any(), eq(snapshotId))).thenReturn(created);

        int applied = useCase().execute(tenantId, branchId, snapshotId, List.of(fresh.getId(), differs.getId()));

        assertThat(applied).isEqualTo(2);
        ArgumentCaptor<NewTarbilStockItem> item = ArgumentCaptor.forClass(NewTarbilStockItem.class);
        verify(port).createFromTarbil(eq(branchId), item.capture(), eq(snapshotId));
        assertThat(item.getValue().category()).isEqualTo("İlaç");
        assertThat(item.getValue().tarbilSystem()).isEqualTo("VETILAC_MEDICINE");
        verify(port).syncFromTarbil(eq(existing), eq(10), any(TarbilStockLink.class), eq(snapshotId));
        assertThat(fresh.getAppliedInventoryItemId()).isEqualTo(created);
        assertThat(differs.getAppliedInventoryItemId()).isEqualTo(existing);
    }

    @Test
    void should_createOneItemWithSummedQuantity_when_sameLotSpansTwoRows() {
        UUID snapshotId = UUID.randomUUID();
        TarbilStockSnapshotLine first = line(1, "Drontal", "L1", 3);
        TarbilStockSnapshotLine second = line(2, "Drontal", "L1", 5);
        when(repository.findByIdForUpdate(snapshotId)).thenReturn(Optional.of(snapshot(TarbilStockSystem.VETILAC_MEDICINE)));
        when(repository.findLines(snapshotId)).thenReturn(List.of(first, second));
        when(port.listForBranch(branchId)).thenReturn(List.of());
        UUID created = UUID.randomUUID();
        when(port.createFromTarbil(eq(branchId), any(), eq(snapshotId))).thenReturn(created);

        int applied = useCase().execute(tenantId, branchId, snapshotId, List.of(first.getId()));

        ArgumentCaptor<NewTarbilStockItem> item = ArgumentCaptor.forClass(NewTarbilStockItem.class);
        verify(port, times(1)).createFromTarbil(eq(branchId), item.capture(), eq(snapshotId));
        assertThat(item.getValue().quantity()).isEqualTo(8);
        verify(port, never()).syncFromTarbil(any(), anyInt(), any(), any());
        assertThat(applied).isEqualTo(1);
        assertThat(second.getAppliedInventoryItemId()).isEqualTo(created);
    }

    @Test
    void should_syncExistingLotlessItem_insteadOfCreatingAgain() {
        UUID snapshotId = UUID.randomUUID();
        TarbilStockSnapshotLine lotless = line(1, "Drontal", null, 6);
        UUID existing = UUID.randomUUID();
        when(repository.findByIdForUpdate(snapshotId)).thenReturn(Optional.of(snapshot(TarbilStockSystem.VETILAC_MEDICINE)));
        when(repository.findLines(snapshotId)).thenReturn(List.of(lotless));
        when(port.listForBranch(branchId)).thenReturn(List.of(new InventoryStockView(existing, "Drontal", null, null, 4, "Drontal")));

        useCase().execute(tenantId, branchId, snapshotId, List.of(lotless.getId()));

        verify(port, never()).createFromTarbil(any(), any(), any());
        verify(port).syncFromTarbil(eq(existing), eq(6), any(TarbilStockLink.class), eq(snapshotId));
    }

    @Test
    void should_skipAlreadyAppliedLines() {
        UUID snapshotId = UUID.randomUUID();
        TarbilStockSnapshotLine done = line(1, "Drontal", "L1", 3);
        done.markApplied(UUID.randomUUID(), Instant.now());
        when(repository.findByIdForUpdate(snapshotId)).thenReturn(Optional.of(snapshot(TarbilStockSystem.HBSAPP_VACCINE)));
        when(repository.findLines(snapshotId)).thenReturn(List.of(done));
        when(port.listForBranch(branchId)).thenReturn(List.of());

        assertThat(useCase().execute(tenantId, branchId, snapshotId, List.of(done.getId()))).isZero();
        verify(port, never()).createFromTarbil(any(), any(), any());
        verify(port, never()).syncFromTarbil(any(), anyInt(), any(), any());
    }

    @Test
    void should_throwNotFound_when_snapshotBelongsToAnotherTenant() {
        UUID snapshotId = UUID.randomUUID();
        when(repository.findByIdForUpdate(snapshotId)).thenReturn(Optional.of(
            TarbilStockSnapshot.take(UUID.randomUUID(), TarbilStockSystem.HBSAPP_VACCINE, UUID.randomUUID(), Instant.now())));

        assertThatThrownBy(() -> useCase().execute(tenantId, branchId, snapshotId, List.of(UUID.randomUUID())))
            .isInstanceOf(TarbilStockSnapshotNotFoundException.class);
        verify(port, never()).listForBranch(any());
    }
}
