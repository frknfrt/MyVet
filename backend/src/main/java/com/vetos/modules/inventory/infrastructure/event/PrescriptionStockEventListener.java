package com.vetos.modules.inventory.infrastructure.event;

import com.vetos.modules.encounter.domain.event.PrescriptionCancelledEvent;
import com.vetos.modules.encounter.domain.event.PrescriptionIssuedEvent;
import com.vetos.modules.inventory.application.PendingDeductionService;
import com.vetos.modules.inventory.domain.StockReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Receteden stoktan verilen kalemler TARBIL dusumunu bekler; iptalde bekleyen kapanir (spec 2026-10-09 S5.6). */
@Component
@RequiredArgsConstructor
class PrescriptionStockEventListener {

    private final PendingDeductionService pending;

    @EventListener
    void onIssued(PrescriptionIssuedEvent event) {
        event.stockItems().forEach(i -> pending.open(i.inventoryItemId(), i.quantity(), StockReferenceType.PRESCRIPTION, event.prescriptionId()));
    }

    @EventListener
    void onCancelled(PrescriptionCancelledEvent event) {
        pending.cancel(StockReferenceType.PRESCRIPTION, event.prescriptionId());
    }
}
