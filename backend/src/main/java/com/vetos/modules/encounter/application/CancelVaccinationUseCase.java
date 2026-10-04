package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.encounter.domain.event.VaccinationCancelledEvent;
import com.vetos.modules.encounter.domain.exception.VaccinationRecordNotFoundException;
import com.vetos.platform.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CancelVaccinationUseCase {

    private final VaccinationRecordRepository vaccinationRecordRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void execute(UUID vaccinationRecordId) {
        VaccinationRecord record = vaccinationRecordRepository.findById(vaccinationRecordId)
            .orElseThrow(() -> new VaccinationRecordNotFoundException(vaccinationRecordId));
        record.cancel();
        vaccinationRecordRepository.save(record);
        // Stoktan dusulmus adet varsa inventory geri ekler (dusulmemisse -- SCHEDULED iptali -- hicbir sey yapmaz).
        eventPublisher.publish(new VaccinationCancelledEvent(record.getId(), record.getInventoryItemId()));
    }
}
