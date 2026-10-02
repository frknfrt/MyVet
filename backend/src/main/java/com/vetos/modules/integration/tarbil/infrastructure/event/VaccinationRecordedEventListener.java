package com.vetos.modules.integration.tarbil.infrastructure.event;

import com.vetos.modules.encounter.domain.event.VaccinationRecordedEvent;
import com.vetos.modules.integration.tarbil.application.QueueTarbilSyncUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class VaccinationRecordedEventListener {

    private final QueueTarbilSyncUseCase queueTarbilSyncUseCase;

    @EventListener
    void onVaccinationRecorded(VaccinationRecordedEvent event) {
        queueTarbilSyncUseCase.queueVaccination(event.patientId(), event.vaccinationRecordId());
    }
}
