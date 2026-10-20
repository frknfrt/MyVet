package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.RevenueSummary;
import com.vetos.modules.billing.domain.BranchRevenueAggregate;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.SalesAggregate;
import com.vetos.modules.tenant.domain.BranchLookupPort;
import com.vetos.modules.tenant.domain.BranchSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetRevenueSummaryUseCaseTest {

    private static final UUID TENANT = UUID.randomUUID();
    // 2026-10-31T22:30Z = Istanbul 2026-11-01 01:30 -> "bu ay" KASIM (UTC'ye gore Ekim olurdu).
    private static final Clock NOV_1_NIGHT = Clock.fixed(Instant.parse("2026-10-31T22:30:00Z"), ZoneOffset.UTC);
    private static final Instant NOV_START = Instant.parse("2026-10-31T21:00:00Z");
    private static final Instant DEC_START = Instant.parse("2026-11-30T21:00:00Z");
    private static final Instant OCT_START = Instant.parse("2026-09-30T21:00:00Z");

    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final BranchLookupPort branchLookupPort = mock(BranchLookupPort.class);

    @Test
    void should_useIstanbulMonthBoundaries_and_dbAggregates() {
        UUID merkez = UUID.randomUUID();
        UUID sube = UUID.randomUUID();
        when(invoiceRepository.sumIssuedBetween(eq(TENANT), any(), any(), any())).thenReturn(new SalesAggregate(null, 0));
        when(invoiceRepository.sumIssuedBetween(eq(TENANT), any(), eq(NOV_START), eq(DEC_START)))
            .thenReturn(new SalesAggregate(new BigDecimal("700"), 3));
        when(invoiceRepository.sumIssuedBetween(eq(TENANT), any(), eq(OCT_START), eq(NOV_START)))
            .thenReturn(new SalesAggregate(new BigDecimal("400"), 2));
        when(invoiceRepository.sumIssuedByBranchBetween(eq(TENANT), any(), eq(NOV_START), eq(DEC_START)))
            .thenReturn(List.of(new BranchRevenueAggregate(sube, new BigDecimal("200")), new BranchRevenueAggregate(merkez, new BigDecimal("500"))));
        when(branchLookupPort.findSummaryById(merkez)).thenReturn(new BranchSummary(merkez, "Merkez"));
        when(branchLookupPort.findSummaryById(sube)).thenReturn(new BranchSummary(sube, "Sube"));

        RevenueSummary result = new GetRevenueSummaryUseCase(invoiceRepository, branchLookupPort, NOV_1_NIGHT).execute(TENANT);

        assertThat(result.monthlyTrend()).extracting(m -> m.month())
            .containsExactly("2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11");
        assertThat(result.monthlyTrend().get(0).revenue()).isEqualByComparingTo("0");
        assertThat(result.currentMonthRevenue()).isEqualByComparingTo("700");
        assertThat(result.previousMonthRevenue()).isEqualByComparingTo("400");
        assertThat(result.branchBreakdown()).extracting(b -> b.branchName()).containsExactly("Merkez", "Sube");
    }
}
