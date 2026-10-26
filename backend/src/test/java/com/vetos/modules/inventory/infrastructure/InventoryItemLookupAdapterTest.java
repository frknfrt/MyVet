package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import org.junit.jupiter.api.Test;
import com.vetos.modules.inventory.domain.StockUnit;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryItemLookupAdapterTest {

    @Mock private InventoryItemRepository items;

    @Test
    void should_preferTarbilName_andFallBackToItemName() {
        UUID linked = UUID.randomUUID();
        UUID plain = UUID.randomUUID();
        InventoryItem a = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID(), "Kuduz aşısı", "Aşı", null, 1, 0, null, "L1", null);
        a.linkTarbil("HBSAPP_VACCINE", "Biocan R", "Flakon");
        InventoryItem b = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID(), "Nobivac", "Aşı", null, 1, 0, null, "L2", null);
        when(items.findById(linked)).thenReturn(Optional.of(a));
        when(items.findById(plain)).thenReturn(Optional.of(b));

        InventoryItemLookupAdapter adapter = new InventoryItemLookupAdapter(items);

        assertThat(adapter.findTarbilProductName(linked)).contains("Biocan R");
        assertThat(adapter.findTarbilProductName(plain)).contains("Nobivac");
        assertThat(adapter.findTarbilProductName(null)).isEmpty();
    }

    private InventoryItem vaccine(StockUnit unit, String size, String opened) {
        InventoryItem i = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID(), "Biocan R", "Aşı", null, 1, 0, null, "L1", null);
        i.setStockUnit(unit, size == null ? null : new java.math.BigDecimal(size), false);
        i.syncTo(1, new java.math.BigDecimal(opened));
        return i;
    }

    @Test
    void vaccine_adetUnit_isWhole_evenWithOpened() {
        UUID adet = UUID.randomUUID();
        UUID flakon = UUID.randomUUID();
        UUID lessThanOne = UUID.randomUUID();
        when(items.findById(adet)).thenReturn(Optional.of(vaccine(StockUnit.ADET, null, "3")));
        when(items.findById(flakon)).thenReturn(Optional.of(vaccine(StockUnit.FLAKON, "20", "3")));
        when(items.findById(lessThanOne)).thenReturn(Optional.of(vaccine(StockUnit.FLAKON, "20", "0.5")));
        InventoryItemLookupAdapter adapter = new InventoryItemLookupAdapter(items);

        assertThat(adapter.findVaccineStockSource(adet)).contains("WHOLE");
        assertThat(adapter.findVaccineStockSource(flakon)).contains("OPEN");
        assertThat(adapter.findVaccineStockSource(lessThanOne)).contains("WHOLE");
        assertThat(adapter.findVaccineStockSource(null)).isEmpty();
    }
}
