package com.vetos.modules.inventory.infrastructure.event;

import com.vetos.modules.encounter.domain.event.EncounterFinalizedEvent;
import com.vetos.modules.inventory.application.AutoDeductStockForEncounterUseCase;
import com.vetos.platform.feature.Feature;
import com.vetos.platform.feature.FeatureAccessPort;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Plan ozelligi (INVENTORY) kapaliysa sessizce atlar (spec 2026-10-12 plan ozellik kisitlamasi 4.4). */
@Component("inventoryEncounterFinalizedEventListener")
@Slf4j
@RequiredArgsConstructor
class EncounterFinalizedEventListener {

    private final AutoDeductStockForEncounterUseCase autoDeductStockForEncounterUseCase;
    private final FeatureAccessPort featureAccess;

    @EventListener
    void onEncounterFinalized(EncounterFinalizedEvent event) {
        if (!featureAccess.isEnabled(TenantContext.current(), Feature.INVENTORY)) {
            log.info("Stok modulu planda kapali, muayene stok dusumu atlandi: encounterId={}", event.encounterId());
            return;
        }
        var usedItems = event.usedItems().stream()
            .map(i -> new AutoDeductStockForEncounterUseCase.UsedItem(i.inventoryItemId(), i.quantity()))
            .toList();
        autoDeductStockForEncounterUseCase.execute(event.encounterId(), usedItems);
    }
}
