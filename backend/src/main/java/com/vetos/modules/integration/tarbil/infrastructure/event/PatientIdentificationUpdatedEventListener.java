package com.vetos.modules.integration.tarbil.infrastructure.event;

import com.vetos.modules.integration.tarbil.application.QueueTarbilSyncUseCase;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncType;
import com.vetos.modules.patient.domain.event.PatientIdentificationUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PatientIdentificationUpdatedEventListener {

    private final QueueTarbilSyncUseCase queueTarbilSyncUseCase;

    @EventListener
    void onPatientIdentificationUpdated(PatientIdentificationUpdatedEvent event) {
        if (event.tarbilAnimalId() == null || event.tarbilAnimalId().isBlank()) {
            return;
        }
        String payload = "{\"microchipNumber\":\"%s\",\"tarbilAnimalId\":\"%s\"}"
            .formatted(event.microchipNumber(), event.tarbilAnimalId());
        queueTarbilSyncUseCase.execute(event.patientId(), TarbilSyncType.IDENTIFICATION, payload);
    }
}
