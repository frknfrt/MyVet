package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.RecordVaccinationCommand;
import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.encounter.domain.event.VaccinationRecordedEvent;
import com.vetos.platform.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecordVaccinationUseCase {

    private final VaccinationRecordRepository vaccinationRecordRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public UUID execute(RecordVaccinationCommand command) {
        VaccinationRecord record = VaccinationRecord.record(
            command.patientId(), command.encounterId(), command.vaccineName(), command.lotNumber(),
            command.administeredDate(), command.nextDueDate(), command.administeredByStaffId()
        );
        vaccinationRecordRepository.save(record);

        eventPublisher.publish(new VaccinationRecordedEvent(
            record.getId(), record.getPatientId(), record.getVaccineName(), record.getAdministeredDate()
        ));
        return record.getId();
    }
}
