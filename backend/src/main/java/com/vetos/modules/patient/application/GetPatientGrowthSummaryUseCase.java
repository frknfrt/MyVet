package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.PatientGrowthSummary;
import com.vetos.modules.patient.domain.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * @docs/requirements.md 4.12 "Raporlama & Analitik" -- dashboard'daki
 * "Isletme ozeti" sekmesindeki sabit "Yeni hasta" degerinin yerini alan
 * gercek hesaplama.
 */
@Service
@RequiredArgsConstructor
public class GetPatientGrowthSummaryUseCase {

    private final PatientRepository patientRepository;

    @Transactional(readOnly = true)
    public PatientGrowthSummary execute(UUID tenantId) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);

        int thisMonth = patientRepository.findByTenantIdAndCreatedAtRange(
            tenantId, startOf(currentMonth), startOf(currentMonth.plusMonths(1))
        ).size();
        int lastMonth = patientRepository.findByTenantIdAndCreatedAtRange(
            tenantId, startOf(previousMonth), startOf(currentMonth)
        ).size();

        return new PatientGrowthSummary(thisMonth, lastMonth);
    }

    private java.time.Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
