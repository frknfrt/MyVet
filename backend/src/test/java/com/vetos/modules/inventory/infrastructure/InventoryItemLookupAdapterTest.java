package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import org.junit.jupiter.api.Test;
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
}
