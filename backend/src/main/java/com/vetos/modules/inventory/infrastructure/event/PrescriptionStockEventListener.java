package com.vetos.modules.inventory.infrastructure.event;

import com.vetos.modules.encounter.domain.event.PrescriptionCancelledEvent;
import com.vetos.modules.encounter.domain.event.PrescriptionIssuedEvent;
import com.vetos.modules.inventory.application.PendingDeductionService;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.exception.InvalidPrescriptionStockItemException;
import com.vetos.platform.feature.Feature;
import com.vetos.platform.feature.FeatureAccessPort;
import com.vetos.platform.feature.FeatureNotInPlanForbiddenException;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Receteden stoktan verilen kalemler TARBIL dusumunu bekler; iptalde bekleyen kapanir (spec 2026-10-09 S5.6). Kalem bu
 * kiracida (filtreli oturum) olmali ve TARBIL'e bagli olmali; degilse recete islemi 422 ile geri alinir (encounter
 * modulu inventory'yi goremedigi icin dogrulama ayni islemdeki bu dinleyicide). Plan ozelligi (INVENTORY) kapaliysa
 * stok kalemli recete reddedilir; iptal her zaman calisir (spec 2026-10-12 plan ozellik kisitlamasi 4.4).
 */
@Component
@RequiredArgsConstructor
class PrescriptionStockEventListener {

    private final PendingDeductionService pending;
    private final InventoryItemRepository items;
    private final FeatureAccessPort featureAccess;

    @EventListener
    void onIssued(PrescriptionIssuedEvent event) {
        if (event.stockItems().isEmpty()) {
            return;
        }
        if (!featureAccess.isEnabled(TenantContext.current(), Feature.INVENTORY)) {
            // Stok kapaliyken "stoktan ver" kalemi dogrulanamaz -- receteyi geri al (spec 4.4).
            throw new FeatureNotInPlanForbiddenException();
        }
        for (PrescriptionIssuedEvent.StockItem stockItem : event.stockItems()) {
            // Bu kiracida (filtreli oturum), TARBIL'e bagli ve recetede yazilan adla ayni kalem (spec 2026-10-12).
            boolean valid = items.findById(stockItem.inventoryItemId())
                .map(i -> i.getTarbilProductName() != null && i.getName().trim().equals(stockItem.name())
                    // Adet ile kullanilan kalemde ondalik miktar olmaz (spec 2026-10-24); ml/g/flakonda olur.
                    && (i.isOpenable() || stockItem.quantity().stripTrailingZeros().scale() <= 0))
                .orElse(false);
            if (!valid) {
                throw new InvalidPrescriptionStockItemException();
            }
        }
        event.stockItems().forEach(i -> pending.open(i.inventoryItemId(), i.quantity(), StockReferenceType.PRESCRIPTION, event.prescriptionId()));
    }

    @EventListener
    void onCancelled(PrescriptionCancelledEvent event) {
        // Iptal ozellik kapaliyken de calisir: plan dusurulmeden once acilan dusum/stok geri alinmali (idempotent).
        pending.cancel(StockReferenceType.PRESCRIPTION, event.prescriptionId());
    }
}
