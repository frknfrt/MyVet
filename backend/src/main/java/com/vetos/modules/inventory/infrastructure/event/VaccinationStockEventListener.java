package com.vetos.modules.inventory.infrastructure.event;

import com.vetos.modules.encounter.domain.event.VaccinationCancelledEvent;
import com.vetos.modules.encounter.domain.event.VaccinationRecordedEvent;
import com.vetos.modules.inventory.application.ApplyVaccinationStockUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Asi uygulandi/iptal edildi -> stok (spec 2026-10-04 P2 S3.3). Ayni islem icinde; kiraci istekten gelir. */
@Component
@RequiredArgsConstructor
class VaccinationStockEventListener {

    private final ApplyVaccinationStockUseCase applyVaccinationStockUseCase;

    @EventListener
    void onVaccinationRecorded(VaccinationRecordedEvent event) {
        applyVaccinationStockUseCase.administered(event.vaccinationRecordId(), event.inventoryItemId());
    }

    @EventListener
    void onVaccinationCancelled(VaccinationCancelledEvent event) {
        applyVaccinationStockUseCase.cancelled(event.vaccinationRecordId(), event.inventoryItemId());
    }
}
