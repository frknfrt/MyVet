package com.vetos.modules.appointment.application;

import com.vetos.modules.appointment.domain.Appointment;
import com.vetos.modules.appointment.domain.AppointmentRepository;
import com.vetos.modules.appointment.domain.AppointmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Faz 1 kapsami: AI degil, basit kural motoru (@docs/implementation-plan.md
 * Modul 3). Sahibin gecmis randevularindaki no-show orani hesaplanir;
 * gecmis yoksa notr bir varsayilan risk uygulanir.
 */
@Component
@RequiredArgsConstructor
class NoShowRiskEstimator {

    private static final BigDecimal DEFAULT_RISK = new BigDecimal("0.10");

    private final AppointmentRepository appointmentRepository;

    BigDecimal estimateFor(UUID ownerId) {
        List<Appointment> history = appointmentRepository.findByOwnerId(ownerId);

        long finished = history.stream()
            .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED || a.getStatus() == AppointmentStatus.NO_SHOW)
            .count();
        if (finished == 0) {
            return DEFAULT_RISK;
        }

        long noShows = history.stream().filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW).count();
        return BigDecimal.valueOf(noShows)
            .divide(BigDecimal.valueOf(finished), 2, RoundingMode.HALF_UP);
    }
}
