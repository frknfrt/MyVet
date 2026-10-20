package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.PatientGrowthSummary;
import com.vetos.modules.patient.domain.PatientRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetPatientGrowthSummaryUseCaseTest {

    private static final UUID TENANT = UUID.randomUUID();
    // Istanbul 2026-11-01 01:30 -> bu ay Kasim
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T22:30:00Z"), ZoneOffset.UTC);

    @Test
    void should_countWithIstanbulMonthBoundaries() {
        PatientRepository repository = mock(PatientRepository.class);
        when(repository.countByTenantIdAndCreatedAtRange(TENANT,
            Instant.parse("2026-10-31T21:00:00Z"), Instant.parse("2026-11-30T21:00:00Z"))).thenReturn(4L);
        when(repository.countByTenantIdAndCreatedAtRange(TENANT,
            Instant.parse("2026-09-30T21:00:00Z"), Instant.parse("2026-10-31T21:00:00Z"))).thenReturn(9L);

        PatientGrowthSummary result = new GetPatientGrowthSummaryUseCase(repository, CLOCK).execute(TENANT);

        assertThat(result.newPatientsThisMonth()).isEqualTo(4);
        assertThat(result.newPatientsLastMonth()).isEqualTo(9);
    }
}
