package com.vetos.modules.inventory.infrastructure.event;

import com.vetos.modules.encounter.domain.event.EncounterFinalizedEvent;
import com.vetos.modules.encounter.domain.event.PrescriptionCancelledEvent;
import com.vetos.modules.encounter.domain.event.PrescriptionIssuedEvent;
import com.vetos.modules.encounter.domain.event.VaccinationCancelledEvent;
import com.vetos.modules.encounter.domain.event.VaccinationRecordedEvent;
import com.vetos.modules.inventory.application.ApplyVaccinationStockUseCase;
import com.vetos.modules.inventory.application.AutoDeductStockForEncounterUseCase;
import com.vetos.modules.inventory.application.PendingDeductionService;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.platform.feature.Feature;
import com.vetos.platform.feature.FeatureAccessPort;
import com.vetos.platform.feature.FeatureNotInPlanForbiddenException;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryFeatureListenersTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock private FeatureAccessPort featureAccess;
    @Mock private AutoDeductStockForEncounterUseCase autoDeduct;
    @Mock private ApplyVaccinationStockUseCase vaccinationStock;
    @Mock private PendingDeductionService pending;
    @Mock private InventoryItemRepository items;

    @BeforeEach
    void setTenant() {
        TenantContext.set(TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private void inventory(boolean enabled) {
        when(featureAccess.isEnabled(TENANT, Feature.INVENTORY)).thenReturn(enabled);
    }

    @Test
    void should_skipEncounterDeduction_when_inventoryDisabled() {
        inventory(false);
        var event = new EncounterFinalizedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            List.of(new EncounterFinalizedEvent.UsedItem(UUID.randomUUID(), java.math.BigDecimal.valueOf(2))));

        new EncounterFinalizedEventListener(autoDeduct, featureAccess).onEncounterFinalized(event);

        verifyNoInteractions(autoDeduct);
    }

    @Test
    void should_deductEncounterStock_when_inventoryEnabled() {
        inventory(true);
        UUID encounterId = UUID.randomUUID();
        var event = new EncounterFinalizedEvent(encounterId, UUID.randomUUID(), UUID.randomUUID(), List.of());

        new EncounterFinalizedEventListener(autoDeduct, featureAccess).onEncounterFinalized(event);

        verify(autoDeduct).execute(eq(encounterId), any());
    }

    @Test
    void should_skipVaccinationStock_when_inventoryDisabled() {
        inventory(false);

        new VaccinationStockEventListener(vaccinationStock, featureAccess).onVaccinationRecorded(new VaccinationRecordedEvent(
            UUID.randomUUID(), UUID.randomUUID(), "Kuduz", LocalDate.now(), UUID.randomUUID(), "LOT1"));

        verifyNoInteractions(vaccinationStock);
    }

    /** Plan dusurulmeden once dusulmus/bekleyen stok iptalde geri alinmali -- ozellik kapali olsa bile. */
    @Test
    void should_stillReverseVaccinationStock_when_inventoryDisabledAfterDowngrade() {
        UUID recordId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        new VaccinationStockEventListener(vaccinationStock, featureAccess).onVaccinationCancelled(new VaccinationCancelledEvent(recordId, itemId));

        verify(vaccinationStock).cancelled(recordId, itemId);
    }

    @Test
    void should_rejectStockPrescription_when_inventoryDisabled() {
        inventory(false);
        var event = new PrescriptionIssuedEvent(UUID.randomUUID(), UUID.randomUUID(), null,
            List.of(new PrescriptionIssuedEvent.StockItem(UUID.randomUUID(), java.math.BigDecimal.ONE, "Metacam")));
        var listener = new PrescriptionStockEventListener(pending, items, featureAccess);

        assertThatThrownBy(() -> listener.onIssued(event)).isInstanceOf(FeatureNotInPlanForbiddenException.class);
        verifyNoInteractions(pending, items);
    }

    @Test
    void should_passThrough_when_inventoryDisabledButNoStockItems() {
        var event = new PrescriptionIssuedEvent(UUID.randomUUID(), UUID.randomUUID(), null, List.of());

        new PrescriptionStockEventListener(pending, items, featureAccess).onIssued(event);

        verifyNoInteractions(pending, items, featureAccess);
    }

    /** Plan dusurulmeden once acilmis bekleyen TARBIL dusumu iptalde kapanmali -- yoksa yukseltmede yanlis dusum yapilir. */
    @Test
    void should_stillCancelPendingDeduction_when_inventoryDisabledAfterDowngrade() {
        UUID prescriptionId = UUID.randomUUID();

        new PrescriptionStockEventListener(pending, items, featureAccess).onCancelled(new PrescriptionCancelledEvent(prescriptionId));

        verify(pending).cancel(com.vetos.modules.inventory.domain.StockReferenceType.PRESCRIPTION, prescriptionId);
    }

    @Test
    void should_rejectFractionalQuantity_when_prescriptionStockItemIsUsedByTheUnit() {
        inventory(true);
        UUID itemId = UUID.randomUUID();
        com.vetos.modules.inventory.domain.InventoryItem tablet = com.vetos.modules.inventory.domain.InventoryItem.create(
            TENANT, UUID.randomUUID(), "Metacam", "İlaç", null, 5, 0, null, "MC7", null);
        tablet.linkTarbil("VETILAC_MEDICINE", "METACAM", "10 tabletlik blister");
        when(items.findById(itemId)).thenReturn(java.util.Optional.of(tablet));
        var event = new PrescriptionIssuedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            List.of(new PrescriptionIssuedEvent.StockItem(itemId, new java.math.BigDecimal("0.5"), "Metacam")));

        assertThatThrownBy(() -> new PrescriptionStockEventListener(pending, items, featureAccess).onIssued(event))
            .isInstanceOf(com.vetos.modules.inventory.domain.exception.InvalidPrescriptionStockItemException.class);
        verifyNoInteractions(pending);
    }

    private com.vetos.modules.inventory.domain.InventoryItem tabletItem() {
        com.vetos.modules.inventory.domain.InventoryItem tablet = com.vetos.modules.inventory.domain.InventoryItem.create(
            TENANT, UUID.randomUUID(), "Baytril Flavour", "İlaç", null, 5, 0, null, "B1", null);
        tablet.linkTarbil("VETILAC_MEDICINE", "BAYTRIL", "10 tabletlik blister");
        tablet.setStockUnit(com.vetos.modules.inventory.domain.StockUnit.TABLET, new java.math.BigDecimal("10"), false);
        return tablet;
    }

    @Test
    void tabletItem_rejectsHalfTablet() {
        inventory(true);
        UUID itemId = UUID.randomUUID();
        var tablet = tabletItem();
        when(items.findById(itemId)).thenReturn(java.util.Optional.of(tablet));
        var event = new PrescriptionIssuedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            List.of(new PrescriptionIssuedEvent.StockItem(itemId, new java.math.BigDecimal("1.5"), "Baytril Flavour")));

        assertThatThrownBy(() -> new PrescriptionStockEventListener(pending, items, featureAccess).onIssued(event))
            .isInstanceOf(com.vetos.modules.inventory.domain.exception.InvalidPrescriptionStockItemException.class);
        verifyNoInteractions(pending);
    }

    @Test
    void tabletItem_acceptsWholeTablets() {
        inventory(true);
        UUID itemId = UUID.randomUUID();
        var tablet = tabletItem();
        when(items.findById(itemId)).thenReturn(java.util.Optional.of(tablet));
        UUID prescriptionId = UUID.randomUUID();
        var event = new PrescriptionIssuedEvent(prescriptionId, UUID.randomUUID(), UUID.randomUUID(),
            List.of(new PrescriptionIssuedEvent.StockItem(itemId, new java.math.BigDecimal("2"), "Baytril Flavour")));

        new PrescriptionStockEventListener(pending, items, featureAccess).onIssued(event);

        verify(pending).open(itemId, new java.math.BigDecimal("2"), com.vetos.modules.inventory.domain.StockReferenceType.PRESCRIPTION, prescriptionId);
    }
}
