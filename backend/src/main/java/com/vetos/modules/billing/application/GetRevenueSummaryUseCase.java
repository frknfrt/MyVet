package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.BranchRevenue;
import com.vetos.modules.billing.application.dto.MonthlyRevenue;
import com.vetos.modules.billing.application.dto.RevenueSummary;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.modules.tenant.domain.BranchLookupPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * @docs/requirements.md 4.12 "Raporlama & Analitik" -- dashboard'daki
 * "Isletme ozeti" sekmesi eskiden sabit/uydurma verilerle doluydu, bu use-case
 * gercek Invoice kayitlarindan hesaplanan aylik ciro trendi ve sube bazli
 * ciro kirilimi uretir.
 * Faz 6 (spec 2026-10-18 §5): toplama veritabaninda (ay basina SUM + sube bazli SUM);
 * ay sinirlari Istanbul saatiyle.
 */
@Service
public class GetRevenueSummaryUseCase {

    static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final Set<InvoiceStatus> REVENUE_STATUSES = EnumSet.of(
        InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID
    );
    private static final int TREND_MONTHS = 6;

    private final InvoiceRepository invoiceRepository;
    private final BranchLookupPort branchLookupPort;
    private final Clock clock;

    @Autowired
    public GetRevenueSummaryUseCase(InvoiceRepository invoiceRepository, BranchLookupPort branchLookupPort) {
        this(invoiceRepository, branchLookupPort, Clock.system(ISTANBUL));
    }

    GetRevenueSummaryUseCase(InvoiceRepository invoiceRepository, BranchLookupPort branchLookupPort, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.branchLookupPort = branchLookupPort;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RevenueSummary execute(UUID tenantId) {
        YearMonth currentMonth = YearMonth.now(clock.withZone(ISTANBUL));

        List<MonthlyRevenue> trend = IntStream.rangeClosed(0, TREND_MONTHS - 1)
            .mapToObj(i -> currentMonth.minusMonths(TREND_MONTHS - 1L - i))
            .map(month -> new MonthlyRevenue(month.toString(), invoiceRepository.sumIssuedBetween(
                tenantId, REVENUE_STATUSES, startOf(month), startOf(month.plusMonths(1))
            ).totalAmount()))
            .toList();

        List<BranchRevenue> branchBreakdown = invoiceRepository.sumIssuedByBranchBetween(
                tenantId, REVENUE_STATUSES, startOf(currentMonth), startOf(currentMonth.plusMonths(1))
            ).stream()
            .map(a -> new BranchRevenue(a.branchId(), branchLookupPort.findSummaryById(a.branchId()).name(), a.totalAmount()))
            .sorted(Comparator.comparing(BranchRevenue::revenue).reversed())
            .toList();

        return new RevenueSummary(
            trend, branchBreakdown, trend.get(TREND_MONTHS - 1).revenue(), trend.get(TREND_MONTHS - 2).revenue()
        );
    }

    private static Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(ISTANBUL).toInstant();
    }
}
