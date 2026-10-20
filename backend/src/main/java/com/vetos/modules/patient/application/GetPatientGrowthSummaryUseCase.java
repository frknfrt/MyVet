package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.PatientGrowthSummary;
import com.vetos.modules.patient.domain.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

/**
 * @docs/requirements.md 4.12 "Raporlama & Analitik" -- dashboard'daki
 * "Isletme ozeti" sekmesindeki sabit "Yeni hasta" degerinin yerini alan
 * gercek hesaplama. Faz 6: sayma veritabaninda, ay sinirlari Istanbul saatiyle.
 */
@Service
public class GetPatientGrowthSummaryUseCase {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final PatientRepository patientRepository;
    private final Clock clock;

    @Autowired
    public GetPatientGrowthSummaryUseCase(PatientRepository patientRepository) {
        this(patientRepository, Clock.system(ISTANBUL));
    }

    GetPatientGrowthSummaryUseCase(PatientRepository patientRepository, Clock clock) {
        this.patientRepository = patientRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PatientGrowthSummary execute(UUID tenantId) {
        YearMonth currentMonth = YearMonth.now(clock.withZone(ISTANBUL));
        YearMonth previousMonth = currentMonth.minusMonths(1);
        long thisMonth = patientRepository.countByTenantIdAndCreatedAtRange(
            tenantId, startOf(currentMonth), startOf(currentMonth.plusMonths(1))
        );
        long lastMonth = patientRepository.countByTenantIdAndCreatedAtRange(
            tenantId, startOf(previousMonth), startOf(currentMonth)
        );
        return new PatientGrowthSummary(Math.toIntExact(thisMonth), Math.toIntExact(lastMonth));
    }

    private static Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(ISTANBUL).toInstant();
    }
}
