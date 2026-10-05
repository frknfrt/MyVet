package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Bir asi serisinin henuz uygulanmamis (ADMINISTERED olmayan) kalan dozlarini
 * iptal eder -- orn. hayvan sahibi serinin devamini istemezse veya protokol
 * degisirse. Zaten yapilmis (ADMINISTERED) dozlara dokunmaz.
 */
@Service
@RequiredArgsConstructor
public class CancelRemainingVaccinationSeriesUseCase {

    private final VaccinationRecordRepository vaccinationRecordRepository;

    @Transactional
    public int execute(UUID seriesId) {
        List<VaccinationRecord> records = vaccinationRecordRepository.findBySeriesId(seriesId);
        int cancelled = 0;
        for (VaccinationRecord record : records) {
            if (record.getStatus() == VaccinationStatus.SCHEDULED) {
                record.cancel();
                vaccinationRecordRepository.save(record);
                cancelled++;
            }
        }
        return cancelled;
    }
}
