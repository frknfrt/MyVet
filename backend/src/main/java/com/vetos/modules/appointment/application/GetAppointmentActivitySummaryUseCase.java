package com.vetos.modules.appointment.application;

import com.vetos.modules.appointment.application.dto.AppointmentActivitySummary;
import com.vetos.modules.appointment.domain.AppointmentRepository;
import com.vetos.modules.appointment.domain.AppointmentStatus;
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
 * "Isletme ozeti" sekmesindeki sabit "Randevu sayisi" / "No-show orani"
 * degerlerinin yerini alan gercek hesaplama. Faz 6: sayma veritabaninda.
 */
@Service
public class GetAppointmentActivitySummaryUseCase {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    @Autowired
    public GetAppointmentActivitySummaryUseCase(AppointmentRepository appointmentRepository) {
        this(appointmentRepository, Clock.system(ISTANBUL));
    }

    GetAppointmentActivitySummaryUseCase(AppointmentRepository appointmentRepository, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AppointmentActivitySummary execute(UUID tenantId) {
        YearMonth currentMonth = YearMonth.now(clock.withZone(ISTANBUL));
        YearMonth previousMonth = currentMonth.minusMonths(1);
        MonthCounts current = countsFor(tenantId, currentMonth);
        MonthCounts previous = countsFor(tenantId, previousMonth);
        return new AppointmentActivitySummary(
            Math.toIntExact(current.total()), Math.toIntExact(previous.total()), current.noShowRate(), previous.noShowRate()
        );
    }

    private MonthCounts countsFor(UUID tenantId, YearMonth month) {
        Instant start = startOf(month);
        Instant end = startOf(month.plusMonths(1));
        return new MonthCounts(
            appointmentRepository.countByTenantIdAndDateRange(tenantId, start, end),
            appointmentRepository.countByTenantIdAndDateRangeAndStatus(tenantId, start, end, AppointmentStatus.NO_SHOW)
        );
    }

    private record MonthCounts(long total, long noShow) {
        double noShowRate() {
            return total == 0 ? 0.0 : noShow / (double) total;
        }
    }

    private static Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(ISTANBUL).toInstant();
    }
}
