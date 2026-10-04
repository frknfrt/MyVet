package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.encounter.domain.event.VaccinationRecordedEvent;
import com.vetos.modules.encounter.domain.exception.VaccinationRecordNotFoundException;
import com.vetos.platform.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarkVaccinationAdministeredUseCase {

    private final VaccinationRecordRepository vaccinationRecordRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void execute(UUID vaccinationRecordId, LocalDate administeredDate) {
        VaccinationRecord record = vaccinationRecordRepository.findById(vaccinationRecordId)
            .orElseThrow(() -> new VaccinationRecordNotFoundException(vaccinationRecordId));

        // Ikinci "uygulandi" (cift tiklama, yeniden deneme) ya da iptal edilmis kayit: olay yok -> stok ikinci kez dusmez.
        if (!record.markAdministered(administeredDate != null ? administeredDate : record.getAdministeredDate())) {
            return;
        }
        vaccinationRecordRepository.save(record);

        eventPublisher.publish(new VaccinationRecordedEvent(
            record.getId(), record.getPatientId(), record.getVaccineName(), record.getAdministeredDate(), record.getInventoryItemId(),
                record.getLotNumber()
        ));
    }
}
