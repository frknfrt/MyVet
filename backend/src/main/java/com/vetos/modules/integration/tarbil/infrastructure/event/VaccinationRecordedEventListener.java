package com.vetos.modules.integration.tarbil.infrastructure.event;

import com.vetos.modules.encounter.domain.event.VaccinationRecordedEvent;
import com.vetos.modules.integration.tarbil.application.QueueTarbilSyncUseCase;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class VaccinationRecordedEventListener {

    private final QueueTarbilSyncUseCase queueTarbilSyncUseCase;

    @EventListener
    void onVaccinationRecorded(VaccinationRecordedEvent event) {
        String payload = "{\"vaccineName\":\"%s\",\"administeredDate\":\"%s\"}"
            .formatted(event.vaccineName(), event.administeredDate());
        queueTarbilSyncUseCase.execute(event.patientId(), TarbilSyncType.VACCINATION, payload);
    }
}
