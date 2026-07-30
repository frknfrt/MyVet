package com.vetos.modules.inventory.infrastructure.event;

import com.vetos.modules.encounter.domain.event.EncounterFinalizedEvent;
import com.vetos.modules.inventory.application.AutoDeductStockForEncounterUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component("inventoryEncounterFinalizedEventListener")
@RequiredArgsConstructor
class EncounterFinalizedEventListener {

    private final AutoDeductStockForEncounterUseCase autoDeductStockForEncounterUseCase;

    @EventListener
    void onEncounterFinalized(EncounterFinalizedEvent event) {
        var usedItems = event.usedItems().stream()
            .map(i -> new AutoDeductStockForEncounterUseCase.UsedItem(i.inventoryItemId(), i.quantity()))
            .toList();
        autoDeductStockForEncounterUseCase.execute(event.encounterId(), usedItems);
    }
}
