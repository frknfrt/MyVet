package com.vetos.modules.encounter.domain;

import com.vetos.modules.encounter.domain.DrugCatalog;
import com.vetos.modules.encounter.domain.DrugCatalogRepository;
import com.vetos.modules.encounter.domain.DrugRoute;
import com.vetos.modules.encounter.domain.PrescriptionItem;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrescriptionItemNamesTest {

    private final DrugCatalogRepository catalog = mock(DrugCatalogRepository.class);
    private final UUID drug = UUID.randomUUID();

    private PrescriptionItem item(UUID drugId, String drugName) {
        return PrescriptionItem.add(UUID.randomUUID(), UUID.randomUUID(), drugId, drugName, "1", "1", 1, DrugRoute.ORAL, null, null);
    }

    @Test
    void usesTheStoredNameWithoutTouchingTheCatalog() {
        assertThat(PrescriptionItemNames.of(item(null, "Metacam"), catalog)).isEqualTo("Metacam");
        verify(catalog, never()).findById(any());
    }

    @Test
    void prefersTheCatalogNameWhenBothAreStored() {
        DrugCatalog amox = mock(DrugCatalog.class);
        when(amox.getName()).thenReturn("Amoksisilin");
        when(catalog.findById(drug)).thenReturn(Optional.of(amox));

        assertThat(PrescriptionItemNames.of(item(drug, "Metacam"), catalog)).isEqualTo("Amoksisilin");
    }

    @Test
    void fallsBackForTheReport() {
        when(catalog.findById(drug)).thenReturn(Optional.empty());

        assertThat(PrescriptionItemNames.of(item(drug, null), catalog)).isNull();
        assertThat(PrescriptionItemNames.of(item(null, "Metacam"), catalog)).isEqualTo("Metacam");
    }
}
