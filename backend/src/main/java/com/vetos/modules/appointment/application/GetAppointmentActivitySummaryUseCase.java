package com.vetos.modules.appointment.application;

import com.vetos.modules.appointment.application.dto.AppointmentActivitySummary;
import com.vetos.modules.appointment.domain.Appointment;
import com.vetos.modules.appointment.domain.AppointmentRepository;
import com.vetos.modules.appointment.domain.AppointmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * @docs/requirements.md 4.12 "Raporlama & Analitik" -- dashboard'daki
 * "Isletme ozeti" sekmesindeki sabit "Randevu sayisi" / "No-show orani"
 * degerlerinin yerini alan gercek hesaplama.
 */
@Service
@RequiredArgsConstructor
public class GetAppointmentActivitySummaryUseCase {

    private final AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    public AppointmentActivitySummary execute(UUID tenantId) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);

        List<Appointment> currentMonthAppointments = appointmentRepository.findByTenantIdAndDateRange(
            tenantId, startOf(currentMonth), startOf(currentMonth.plusMonths(1))
        );
        List<Appointment> previousMonthAppointments = appointmentRepository.findByTenantIdAndDateRange(
            tenantId, startOf(previousMonth), startOf(currentMonth)
        );

        return new AppointmentActivitySummary(
            currentMonthAppointments.size(), previousMonthAppointments.size(),
            noShowRate(currentMonthAppointments), noShowRate(previousMonthAppointments)
        );
    }

    private double noShowRate(List<Appointment> appointments) {
        if (appointments.isEmpty()) return 0.0;
        long noShowCount = appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW).count();
        return noShowCount / (double) appointments.size();
    }

    private java.time.Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
