package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.NewTarbilStockItem;
import com.vetos.modules.inventory.domain.StockMovement;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.TarbilStockLink;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        return new TarbilStockSyncAdapter(items, movements);
    }

    @Test
    void should_createLinkedItemWithInMovement_when_newTarbilLine() {
        when(items.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adapter().createFromTarbil(branchId,
            new NewTarbilStockItem("VETILAC_MEDICINE", "Test İlaç", "Kutu", "İlaç", "L-1", LocalDate.of(2027, 1, 31), 5), ref);

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
        assertThat(item.getValue().getUnit()).isEqualTo("ADET");
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

        adapter().syncFromTarbil(id, 5, new TarbilStockLink("HBSAPP_VACCINE", "Aşı X", "Flakon"), ref);

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

        adapter().syncFromTarbil(id, 5, new TarbilStockLink("HBSAPP_VACCINE", "Aşı Y", null), ref);

        verify(movements, never()).save(any());
        assertThat(item.getTarbilSystem()).isEqualTo("HBSAPP_VACCINE");
    }
}
