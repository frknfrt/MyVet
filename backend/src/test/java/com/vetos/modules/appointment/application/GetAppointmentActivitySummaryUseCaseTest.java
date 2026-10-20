package com.vetos.modules.appointment.application;

import com.vetos.modules.appointment.application.dto.AppointmentActivitySummary;
import com.vetos.modules.appointment.domain.AppointmentRepository;
import com.vetos.modules.appointment.domain.AppointmentStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetAppointmentActivitySummaryUseCaseTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T22:30:00Z"), ZoneOffset.UTC);
    private static final Instant OCT = Instant.parse("2026-09-30T21:00:00Z");
    private static final Instant NOV = Instant.parse("2026-10-31T21:00:00Z");
    private static final Instant DEC = Instant.parse("2026-11-30T21:00:00Z");

    private final AppointmentRepository repository = mock(AppointmentRepository.class);

    @Test
    void should_countTotalsAndNoShows_perIstanbulMonth() {
        when(repository.countByTenantIdAndDateRange(TENANT, NOV, DEC)).thenReturn(8L);
        when(repository.countByTenantIdAndDateRangeAndStatus(TENANT, NOV, DEC, AppointmentStatus.NO_SHOW)).thenReturn(2L);
        when(repository.countByTenantIdAndDateRange(TENANT, OCT, NOV)).thenReturn(10L);
        when(repository.countByTenantIdAndDateRangeAndStatus(TENANT, OCT, NOV, AppointmentStatus.NO_SHOW)).thenReturn(1L);

        AppointmentActivitySummary result = new GetAppointmentActivitySummaryUseCase(repository, CLOCK).execute(TENANT);

        assertThat(result.currentMonthCount()).isEqualTo(8);
        assertThat(result.previousMonthCount()).isEqualTo(10);
        assertThat(result.currentMonthNoShowRate()).isEqualTo(0.25);
        assertThat(result.previousMonthNoShowRate()).isEqualTo(0.1);
    }

    @Test
    void should_returnZeroRate_when_noAppointments() {
        AppointmentActivitySummary result = new GetAppointmentActivitySummaryUseCase(repository, CLOCK).execute(TENANT);

        assertThat(result.currentMonthCount()).isZero();
        assertThat(result.currentMonthNoShowRate()).isEqualTo(0.0);
        assertThat(result.previousMonthNoShowRate()).isEqualTo(0.0);
    }
}
