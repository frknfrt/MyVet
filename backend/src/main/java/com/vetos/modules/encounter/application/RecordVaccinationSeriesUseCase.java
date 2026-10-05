package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.RecordVaccinationSeriesCommand;
import com.vetos.modules.encounter.application.dto.RecordVaccinationSeriesResult;
import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.event.VaccinationRecordedEvent;
import com.vetos.platform.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @docs/requirements.md 4.3 "Asi takviminde periyodik secenek" -- tek
 * cagrida N adet vaccination_records kaydi olusturur (orn. 60 gunde 10 doz).
 * Her dozun kendi tarihi "nextDueDate" olarak da yazilir, boylece
 * SendVaccinationRemindersUseCase her dozdan bir gun once sahibe otomatik
 * hatirlatma gonderir -- ilk doz bugun ADMINISTERED olarak isaretlenirse o
 * dozun hatirlatmaya ihtiyaci olmadigi icin nextDueDate bos birakilir.
 */
@Service
@RequiredArgsConstructor
public class RecordVaccinationSeriesUseCase {

    private static final int MIN_DOSE_COUNT = 2;
    private static final int MAX_DOSE_COUNT = 30;
    private static final int MIN_INTERVAL_DAYS = 1;
    private static final int MAX_INTERVAL_DAYS = 3650;

    private final VaccinationRecordRepository vaccinationRecordRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public RecordVaccinationSeriesResult execute(RecordVaccinationSeriesCommand command) {
        if (command.doseCount() < MIN_DOSE_COUNT || command.doseCount() > MAX_DOSE_COUNT) {
            throw new IllegalArgumentException("Doz sayisi " + MIN_DOSE_COUNT + " ile " + MAX_DOSE_COUNT + " arasinda olmalidir");
        }
        if (command.intervalDays() < MIN_INTERVAL_DAYS || command.intervalDays() > MAX_INTERVAL_DAYS) {
            throw new IllegalArgumentException("Tekrar araligi " + MIN_INTERVAL_DAYS + " ile " + MAX_INTERVAL_DAYS + " gun arasinda olmalidir");
        }

        UUID seriesId = UUID.randomUUID();
        List<VaccinationRecord> records = new ArrayList<>();

        for (int i = 0; i < command.doseCount(); i++) {
            int doseNumber = i + 1;
            var doseDate = command.startDate().plusDays((long) command.intervalDays() * i);
            VaccinationStatus status = i == 0 ? command.firstDoseStatus() : VaccinationStatus.SCHEDULED;
            var nextDueDate = status == VaccinationStatus.SCHEDULED ? doseDate : null;

            records.add(VaccinationRecord.record(
                command.tenantId(), command.patientId(), command.encounterId(), command.vaccineName(), command.lotNumber(),
                doseDate, nextDueDate, command.administeredByStaffId(), status, command.notes(),
                seriesId, doseNumber, command.doseCount()
            ));
        }

        List<VaccinationRecord> saved = vaccinationRecordRepository.saveAll(records);
        for (VaccinationRecord record : saved) {
            if (record.getStatus() == VaccinationStatus.ADMINISTERED) {
                eventPublisher.publish(new VaccinationRecordedEvent(
                    record.getId(), record.getPatientId(), record.getVaccineName(), record.getAdministeredDate(),
                    record.getInventoryItemId(), record.getLotNumber()
                ));
            }
        }
        return new RecordVaccinationSeriesResult(seriesId, saved.stream().map(VaccinationRecord::getId).toList());
    }
}
