package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.TodaySalesSummary;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.modules.billing.domain.SalesAggregate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bugunun tarih araligina/durumlarina gore filtreleme artik JPQL tarafinda
 * (InvoiceJpaRepository#sumIssuedBetween). Bu katmanda dogrulanan sey:
 * repository'nin dogru tenant, durum kumesi ve [bugun 00:00, yarin 00:00)
 * araligi ile cagrildigi ve donen toplamin aynen yansitildigi.
 */
@ExtendWith(MockitoExtension.class)
class GetTodaySalesSummaryUseCaseTest {

    @Mock private InvoiceRepository invoiceRepository;

    private GetTodaySalesSummaryUseCase useCase;

    @Test
    void should_queryTodayRangeWithRevenueStatuses() {
        useCase = new GetTodaySalesSummaryUseCase(invoiceRepository);
        UUID tenantId = UUID.randomUUID();
        when(invoiceRepository.sumIssuedBetween(any(), any(), any(), any()))
            .thenReturn(new SalesAggregate(BigDecimal.valueOf(150), 2));

        TodaySalesSummary result = useCase.execute(tenantId);

        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<InvoiceStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(invoiceRepository).sumIssuedBetween(
            eq(tenantId), statuses.capture(), eq(startOfToday), eq(startOfToday.plusSeconds(86400))
        );
        assertThat(statuses.getValue())
            .containsExactlyInAnyOrder(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID);
        assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(result.saleCount()).isEqualTo(2);
    }

    @Test
    void should_returnZero_when_noInvoicesToday() {
        useCase = new GetTodaySalesSummaryUseCase(invoiceRepository);
        UUID tenantId = UUID.randomUUID();
        // SUM() eslesen satir yoksa null doner; SalesAggregate bunu sifira normalize eder.
        when(invoiceRepository.sumIssuedBetween(any(), any(), any(), any()))
            .thenReturn(new SalesAggregate(null, 0));

        TodaySalesSummary result = useCase.execute(tenantId);

        assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.saleCount()).isZero();
    }
}
