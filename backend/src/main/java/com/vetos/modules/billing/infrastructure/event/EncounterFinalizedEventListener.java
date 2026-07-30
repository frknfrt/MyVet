package com.vetos.modules.billing.infrastructure.event;

import com.vetos.modules.billing.application.AutoCaptureEncounterChargeUseCase;
import com.vetos.modules.encounter.domain.event.EncounterFinalizedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component("billingEncounterFinalizedEventListener")
@RequiredArgsConstructor
class EncounterFinalizedEventListener {

    private final AutoCaptureEncounterChargeUseCase autoCaptureEncounterChargeUseCase;

    @EventListener
    void onEncounterFinalized(EncounterFinalizedEvent event) {
        autoCaptureEncounterChargeUseCase.execute(event.encounterId(), event.patientId(), event.staffUserId());
    }
}
