package com.vetos.modules.inventory.infrastructure.event;

import com.vetos.modules.encounter.domain.event.VaccinationCancelledEvent;
import com.vetos.modules.encounter.domain.event.VaccinationRecordedEvent;
import com.vetos.modules.inventory.application.ApplyVaccinationStockUseCase;
import com.vetos.platform.feature.Feature;
import com.vetos.platform.feature.FeatureAccessPort;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Asi uygulandi/iptal edildi -> stok (spec 2026-10-04 P2 S3.3). Ayni islem icinde; kiraci istekten gelir.
 * Plan ozelligi (INVENTORY) kapaliysa uygulama stogunu atlar; iptal her zaman calisir (spec 2026-10-12 plan ozellik kisitlamasi 4.4).
 */
@Component
@Slf4j
@RequiredArgsConstructor
class VaccinationStockEventListener {

    private final ApplyVaccinationStockUseCase applyVaccinationStockUseCase;
    private final FeatureAccessPort featureAccess;

    @EventListener
    void onVaccinationRecorded(VaccinationRecordedEvent event) {
        if (!featureAccess.isEnabled(TenantContext.current(), Feature.INVENTORY)) {
            log.info("Stok modulu planda kapali, asi stok islemi atlandi: vaccinationRecordId={}", event.vaccinationRecordId());
            return;
        }
        applyVaccinationStockUseCase.administered(event.vaccinationRecordId(), event.inventoryItemId(), event.lotNumber());
    }

    @EventListener
    void onVaccinationCancelled(VaccinationCancelledEvent event) {
        // Iptal ozellik kapaliyken de calisir: plan dusurulmeden once acilan dusum/stok geri alinmali (idempotent).
        applyVaccinationStockUseCase.cancelled(event.vaccinationRecordId(), event.inventoryItemId());
    }
}
