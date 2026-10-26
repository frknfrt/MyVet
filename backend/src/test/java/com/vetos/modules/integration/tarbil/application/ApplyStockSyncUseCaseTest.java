package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilStockLinePendingConflictException;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyStockSyncUseCaseTest {

    @Mock private TarbilStockSnapshotRepository repository;
    @Mock private TarbilStockSyncPort port;
    @Mock private PendingTarbilStockWork pendingWork;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();

    private TarbilStockSnapshot snapshot(TarbilStockSystem system) {
        return TarbilStockSnapshot.take(tenantId, system, UUID.randomUUID(), Instant.now());
    }

    private TarbilStockSnapshotLine line(int no, String product, String lot, int qty) {
        return TarbilStockSnapshotLine.of(null, tenantId, no, product, "Kutu", lot, null, qty, null);
    }

    private ApplyStockSyncUseCase useCase() {
        return new ApplyStockSyncUseCase(repository, port, pendingWork);
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
        when(port.listForBranch(branchId)).thenReturn(List.of(new InventoryStockView(existing, "Rabisin", "R9", null, 4, null, java.math.BigDecimal.ZERO, "ADET")));
        UUID created = UUID.randomUUID();
        when(port.createFromTarbil(eq(branchId), any(), eq(snapshotId))).thenReturn(created);

        int applied = useCase().execute(tenantId, branchId, snapshotId, List.of(fresh.getId(), differs.getId()));

        assertThat(applied).isEqualTo(2);
        ArgumentCaptor<NewTarbilStockItem> item = ArgumentCaptor.forClass(NewTarbilStockItem.class);
        verify(port).createFromTarbil(eq(branchId), item.capture(), eq(snapshotId));
        assertThat(item.getValue().category()).isEqualTo("İlaç");
        assertThat(item.getValue().tarbilSystem()).isEqualTo("VETILAC_MEDICINE");
        verify(port).syncFromTarbil(eq(existing), eq(10), eq(java.math.BigDecimal.ZERO), isNull(), any(TarbilStockLink.class), eq(snapshotId), any());
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
        verify(port, never()).syncFromTarbil(any(), anyInt(), any(), any(), any(), any(), any());
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
        when(port.listForBranch(branchId)).thenReturn(List.of(new InventoryStockView(existing, "Drontal", null, null, 4, "Drontal", java.math.BigDecimal.ZERO, "ADET")));

        useCase().execute(tenantId, branchId, snapshotId, List.of(lotless.getId()));

        verify(port, never()).createFromTarbil(any(), any(), any());
        verify(port).syncFromTarbil(eq(existing), eq(6), eq(java.math.BigDecimal.ZERO), isNull(), any(TarbilStockLink.class), eq(snapshotId), any());
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
        verify(port, never()).syncFromTarbil(any(), anyInt(), any(), any(), any(), any(), any());
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

    @Test
    void should_rejectWholeRequest_when_aSelectedLinesItemHasWorkPendingInTarbil() {
        UUID snapshotId = UUID.randomUUID();
        TarbilStockSnapshotLine fresh = line(1, "Drontal", "L1", 3);
        TarbilStockSnapshotLine differs = line(2, "Biocan DHPPI", "145932", 1);
        UUID existing = UUID.randomUUID();
        when(repository.findByIdForUpdate(snapshotId)).thenReturn(Optional.of(snapshot(TarbilStockSystem.HBSAPP_VACCINE)));
        when(repository.findLines(snapshotId)).thenReturn(List.of(fresh, differs));
        when(port.listForBranch(branchId)).thenReturn(List.of(new InventoryStockView(existing, "Biocan DHPPI", "145932", null, 0, null, java.math.BigDecimal.ZERO, "ADET")));
        when(pendingWork.countByItem(tenantId)).thenReturn(Map.of(existing, 1));

        assertThatThrownBy(() -> useCase().execute(tenantId, branchId, snapshotId, List.of(fresh.getId(), differs.getId())))
            .isInstanceOf(TarbilStockLinePendingConflictException.class);
        verify(port, never()).createFromTarbil(any(), any(), any());
        verify(port, never()).syncFromTarbil(any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void should_applyLine_when_pendingWorkTouchesOnlyAnUnselectedItem() {
        UUID snapshotId = UUID.randomUUID();
        TarbilStockSnapshotLine differs = line(1, "Rabisin", "R9", 10);
        TarbilStockSnapshotLine blocked = line(2, "Biocan DHPPI", "145932", 1);
        UUID rabisin = UUID.randomUUID();
        UUID biocan = UUID.randomUUID();
        when(repository.findByIdForUpdate(snapshotId)).thenReturn(Optional.of(snapshot(TarbilStockSystem.HBSAPP_VACCINE)));
        when(repository.findLines(snapshotId)).thenReturn(List.of(differs, blocked));
        when(port.listForBranch(branchId)).thenReturn(List.of(
            new InventoryStockView(rabisin, "Rabisin", "R9", null, 4, null, java.math.BigDecimal.ZERO, "ADET"),
            new InventoryStockView(biocan, "Biocan DHPPI", "145932", null, 0, null, java.math.BigDecimal.ZERO, "ADET")));
        when(pendingWork.countByItem(tenantId)).thenReturn(Map.of(biocan, 1));

        int applied = useCase().execute(tenantId, branchId, snapshotId, List.of(differs.getId()));

        assertThat(applied).isEqualTo(1);
        verify(port).syncFromTarbil(eq(rabisin), eq(10), isNull(), isNull(), any(TarbilStockLink.class), eq(snapshotId), any());
    }

    @Test
    void vaccineSnapshotWithoutPartStockFlag_keepsOpenedQuantity() {
        UUID snapshotId = UUID.randomUUID();
        TarbilStockSnapshot s = TarbilStockSnapshot.take(tenantId, TarbilStockSystem.HBSAPP_VACCINE, UUID.randomUUID(), Instant.now(), false);
        TarbilStockSnapshotLine l = TarbilStockSnapshotLine.of(null, tenantId, 1, "Biocan DHPPI", "Kutusunda 20 adet flakon", "145932", null, 1, null, false, 20);
        UUID existing = UUID.randomUUID();
        when(repository.findByIdForUpdate(snapshotId)).thenReturn(Optional.of(s));
        when(repository.findLines(snapshotId)).thenReturn(List.of(l));
        when(port.listForBranch(branchId)).thenReturn(List.of(new InventoryStockView(existing, "Biocan DHPPI", "145932", null, 3, null, new java.math.BigDecimal("7"), "FLAKON")));

        useCase().execute(tenantId, branchId, snapshotId, List.of(l.getId()));

        verify(port).syncFromTarbil(eq(existing), eq(1), isNull(), eq(20), any(TarbilStockLink.class), eq(snapshotId), any());
    }

    @Test
    void clavonApply_keeps49ml() {
        UUID snapshotId = UUID.randomUUID();
        TarbilStockSnapshot s = snapshot(TarbilStockSystem.VETILAC_MEDICINE);
        TarbilStockSnapshotLine l = TarbilStockSnapshotLine.of(null, tenantId, 1, "CLAVON = Clavon", "50 ml'lik renksiz cam şişeler", "63016", null, 0, new java.math.BigDecimal("49.000"));
        UUID existing = UUID.randomUUID();
        when(repository.findByIdForUpdate(snapshotId)).thenReturn(Optional.of(s));
        when(repository.findLines(snapshotId)).thenReturn(List.of(l));
        when(port.listForBranch(branchId)).thenReturn(List.of(new InventoryStockView(existing, "CLAVON = Clavon", "63016", null, 1, null, java.math.BigDecimal.ZERO, "ADET")));

        useCase().execute(tenantId, branchId, snapshotId, List.of(l.getId()));

        ArgumentCaptor<java.math.BigDecimal> opened = ArgumentCaptor.forClass(java.math.BigDecimal.class);
        verify(port).syncFromTarbil(eq(existing), eq(0), opened.capture(), isNull(), any(TarbilStockLink.class), eq(snapshotId), any());
        assertThat(opened.getValue()).isEqualByComparingTo("49");
    }
}
