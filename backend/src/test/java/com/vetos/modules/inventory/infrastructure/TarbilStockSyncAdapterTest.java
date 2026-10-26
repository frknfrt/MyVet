package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.NewTarbilStockItem;
import com.vetos.modules.inventory.domain.StockMovement;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.TarbilStockLink;
import com.vetos.modules.inventory.domain.StockUnit;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TarbilStockSyncAdapterTest {

    @Mock private InventoryItemRepository items;
    @Mock private StockMovementRepository movements;
    @Mock private com.vetos.modules.inventory.domain.PendingStockDeductionRepository pending;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final UUID ref = UUID.randomUUID();

    @BeforeEach
    void setTenant() {
        TenantContext.set(tenantId);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private TarbilStockSyncAdapter adapter() {
        return new TarbilStockSyncAdapter(items, movements, pending);
    }

    @Test
    void should_createLinkedItemWithInMovement_when_newTarbilLine() {
        when(items.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adapter().createFromTarbil(branchId,
            new NewTarbilStockItem("VETILAC_MEDICINE", "Test İlaç", "Kutu", "İlaç", "L-1", LocalDate.of(2027, 1, 31), 5, null, null), ref);

        ArgumentCaptor<InventoryItem> item = ArgumentCaptor.forClass(InventoryItem.class);
        verify(items).save(item.capture());
        assertThat(item.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(item.getValue().getBranchId()).isEqualTo(branchId);
        assertThat(item.getValue().getName()).isEqualTo("Test İlaç");
        assertThat(item.getValue().getLotNumber()).isEqualTo("L-1");
        assertThat(item.getValue().getQuantityOnHand()).isEqualTo(5);
        assertThat(item.getValue().getTarbilSystem()).isEqualTo("VETILAC_MEDICINE");
        assertThat(item.getValue().getTarbilProductName()).isEqualTo("Test İlaç");
        assertThat(item.getValue().getTarbilPresentation()).isEqualTo("Kutu");
        assertThat(item.getValue().getUnit()).isEqualTo(com.vetos.modules.inventory.domain.StockUnit.ADET);
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements).save(movement.capture());
        assertThat(movement.getValue().getMovementType()).isEqualTo(StockMovementType.IN);
        assertThat(movement.getValue().getQuantity()).isEqualTo(5);
        assertThat(movement.getValue().getReferenceType()).isEqualTo(StockReferenceType.TARBIL_SYNC);
        assertThat(movement.getValue().getReferenceId()).isEqualTo(ref);
    }

    @Test
    void should_recordOutMovement_when_tarbilHasLessThanVetly() {
        UUID id = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(tenantId, branchId, "Aşı", "Aşı", null, 8, 0, null, "L-2", null);
        when(items.findById(id)).thenReturn(Optional.of(item));

        adapter().syncFromTarbil(id, 5, null, null, new TarbilStockLink("HBSAPP_VACCINE", "Aşı X", "Flakon"), ref);

        assertThat(item.getQuantityOnHand()).isEqualTo(5);
        assertThat(item.getTarbilProductName()).isEqualTo("Aşı X");
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements).save(movement.capture());
        assertThat(movement.getValue().getMovementType()).isEqualTo(StockMovementType.OUT);
        assertThat(movement.getValue().getQuantity()).isEqualTo(3);
        verify(items).save(item);
    }

    @Test
    void should_onlyLink_when_quantitiesAlreadyEqual() {
        UUID id = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(tenantId, branchId, "Aşı", "Aşı", null, 5, 0, null, "L-3", null);
        when(items.findById(id)).thenReturn(Optional.of(item));

        adapter().syncFromTarbil(id, 5, null, null, new TarbilStockLink("HBSAPP_VACCINE", "Aşı Y", null), ref);

        verify(movements, never()).save(any());
        assertThat(item.getTarbilSystem()).isEqualTo("HBSAPP_VACCINE");
    }

    @Test
    void createFromTarbil_setsGuessedUnitAndOpened() {
        when(items.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adapter().createFromTarbil(branchId, new NewTarbilStockItem("VETILAC_MEDICINE", "CLAVON = Clavon",
            "50 ml'lik renksiz cam şişeler", "İlaç", "63016", LocalDate.of(2027, 4, 27), 0, new BigDecimal("49"), null), ref);

        ArgumentCaptor<InventoryItem> item = ArgumentCaptor.forClass(InventoryItem.class);
        verify(items).save(item.capture());
        assertThat(item.getValue().getUnit()).isEqualTo(StockUnit.ML);
        assertThat(item.getValue().getPackageSize()).isEqualByComparingTo("50");
        assertThat(item.getValue().getQuantityOnHand()).isZero();
        assertThat(item.getValue().getOpenedQuantity()).isEqualByComparingTo("49");
        assertThat(item.getValue().isUnitManual()).isFalse();
    }

    @Test
    void manualUnit_isNotOverwrittenBySync() {
        UUID id = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(tenantId, branchId, "Clavon", "İlaç", null, 1, 0, null, "63016", null);
        item.setStockUnit(StockUnit.ADET, null, true);
        when(items.findById(id)).thenReturn(Optional.of(item));

        adapter().syncFromTarbil(id, 1, BigDecimal.ZERO, null,
            new TarbilStockLink("VETILAC_MEDICINE", "CLAVON = Clavon", "50 ml'lik renksiz cam şişeler"), ref);

        assertThat(item.getUnit()).isEqualTo(StockUnit.ADET);
        assertThat(item.isUnitManual()).isTrue();
    }

    @Test
    void syncFromTarbil_writesMovementWithOpenedDelta() {
        UUID id = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(tenantId, branchId, "Clavon", "İlaç", null, 1, 0, null, "63016", null);
        when(items.findById(id)).thenReturn(Optional.of(item));

        adapter().syncFromTarbil(id, 0, new BigDecimal("49"), null,
            new TarbilStockLink("VETILAC_MEDICINE", "CLAVON = Clavon", "50 ml'lik renksiz cam şişeler"), ref);

        assertThat(item.getUnit()).isEqualTo(StockUnit.ML);
        assertThat(item.getQuantityOnHand()).isZero();
        assertThat(item.getOpenedQuantity()).isEqualByComparingTo("49");
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements).save(movement.capture());
        assertThat(movement.getValue().getMovementType()).isEqualTo(StockMovementType.OUT);
        assertThat(movement.getValue().getQuantity()).isEqualTo(1);
        assertThat(movement.getValue().getOpenedDelta()).isEqualByComparingTo("49");
        assertThat(movement.getValue().getReferenceType()).isEqualTo(StockReferenceType.TARBIL_SYNC);
    }

    @Test
    void vaccineDoses_setPackageSize() {
        UUID id = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(tenantId, branchId, "Biocan DHPPI", "Aşı", null, 1, 0, null, "145932", null);
        when(items.findById(id)).thenReturn(Optional.of(item));

        adapter().syncFromTarbil(id, 1, null, 20,
            new TarbilStockLink("HBSAPP_VACCINE", "Biocan DHPPI", "1 ml flakonda sulandırıcısı ile birlikte, Kutusunda 20 adet flakon"), ref);

        assertThat(item.getUnit()).isEqualTo(StockUnit.FLAKON);
        assertThat(item.getPackageSize()).isEqualByComparingTo("20");
        verify(movements, never()).save(any());
    }

    // Değerlendirme I4: eşleşen (seçilemeyen) ürüne de birim tahmini.
    private InventoryItem tenidur() {
        InventoryItem i = InventoryItem.create(tenantId, branchId, "Tenidur", "İlaç", null, 2, 0, null, "251227", null);
        when(items.findById(i.getId())).thenReturn(Optional.of(i));
        return i;
    }

    @Test
    void refreshFromSnapshot_setsTheGuessedUnitOnAnAutoItem_withoutAMovement() {
        InventoryItem i = tenidur();

        adapter().refreshFromSnapshot(i.getId(), "VETILAC_MEDICINE", "100 ml'lik cam şişe", null, null);

        assertThat(i.getUnit()).isEqualTo(StockUnit.ML);
        assertThat(i.getPackageSize()).isEqualByComparingTo("100");
        assertThat(i.isUnitManual()).isFalse();
        assertThat(i.getQuantityOnHand()).isEqualTo(2);
        verify(items).save(i);
        verify(movements, never()).save(any());
    }

    @Test
    void refreshFromSnapshot_leavesAManualUnitAlone() {
        InventoryItem i = tenidur();
        i.setStockUnit(StockUnit.ADET, null, true);

        adapter().refreshFromSnapshot(i.getId(), "VETILAC_MEDICINE", "100 ml'lik cam şişe", null, null);

        assertThat(i.getUnit()).isEqualTo(StockUnit.ADET);
        verify(items, never()).save(any());
    }

    @Test
    void refreshFromSnapshot_waitsWhileDeductionsArePending() {
        InventoryItem i = tenidur();
        when(pending.existsPendingForItem(i.getId())).thenReturn(true);

        adapter().refreshFromSnapshot(i.getId(), "VETILAC_MEDICINE", "100 ml'lik cam şişe", null, null);

        assertThat(i.getUnit()).isEqualTo(StockUnit.ADET);
        verify(items, never()).save(any());
    }

    @Test
    void refresh_adetTabletBecomesTablet_keepsClosedBoxes() {
        InventoryItem i = InventoryItem.create(tenantId, branchId, "Baytril Flavour", "İlaç", null, 3, 0, null, "B1", null);
        when(items.findById(i.getId())).thenReturn(Optional.of(i));

        adapter().refreshFromSnapshot(i.getId(), "VETILAC_MEDICINE", "Karton kutu içerisinde 10 tabletlik blisterlerde", null, null);

        assertThat(i.getUnit()).isEqualTo(StockUnit.TABLET);
        assertThat(i.getPackageSize()).isEqualByComparingTo("10");
        assertThat(i.getQuantityOnHand()).isEqualTo(3);
        assertThat(i.getOpenedQuantity()).isEqualByComparingTo("0");
        verify(movements, never()).save(any());
    }

    @Test
    void refresh_setsPieceForPipetBox() {
        InventoryItem i = InventoryItem.create(tenantId, branchId, "Fiproes Kedi", "İlaç", null, 22, 0, null, "F1", null);
        when(items.findById(i.getId())).thenReturn(Optional.of(i));

        adapter().refreshFromSnapshot(i.getId(), "VETILAC_MEDICINE", "0.5 ml’lik pipetler 3 adetlik karton kutuda", null, null);

        assertThat(i.getUnit()).isEqualTo(StockUnit.ML);
        assertThat(i.getPackageSize()).isEqualByComparingTo("1.5");
        assertThat(i.getPieceSize()).isEqualByComparingTo("0.5");
        assertThat(i.getPieceLabel()).isEqualTo("pipet");
    }

    @Test
    void refresh_writesOpenedExpiry_evenWhenUnitManual() {
        InventoryItem i = tenidur();
        i.setStockUnit(StockUnit.ML, new BigDecimal("20"), true);
        i.syncTo(2, new BigDecimal("18"));

        adapter().refreshFromSnapshot(i.getId(), "VETILAC_MEDICINE", "20 ml amber flakon", null, LocalDate.of(2026, 11, 1));

        assertThat(i.getOpenedExpiryDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(i.isUnitManual()).isTrue();
        verify(items).save(i);
    }

    @Test
    void syncFromTarbil_writesOpenedExpiryAfterOpenedArrives() {
        UUID id = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(tenantId, branchId, "Clavon", "İlaç", null, 1, 0, null, "63016", null);
        when(items.findById(id)).thenReturn(Optional.of(item));

        adapter().syncFromTarbil(id, 0, new BigDecimal("49"), null,
            new TarbilStockLink("VETILAC_MEDICINE", "CLAVON = Clavon", "50 ml'lik renksiz cam şişeler"), ref, LocalDate.of(2026, 11, 20));

        assertThat(item.getOpenedExpiryDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void createFromTarbil_carriesOpenedExpiry() {
        when(items.save(any())).thenAnswer(inv -> inv.getArgument(0));
        adapter().createFromTarbil(branchId, new NewTarbilStockItem("VETILAC_MEDICINE", "CLAVON = Clavon", "50 ml'lik renksiz cam şişeler",
            "İlaç", "63016", null, 0, new BigDecimal("49"), null, LocalDate.of(2026, 11, 20)), ref);

        org.mockito.ArgumentCaptor<InventoryItem> saved = org.mockito.ArgumentCaptor.forClass(InventoryItem.class);
        verify(items).save(saved.capture());
        assertThat(saved.getValue().getOpenedExpiryDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    }
}
