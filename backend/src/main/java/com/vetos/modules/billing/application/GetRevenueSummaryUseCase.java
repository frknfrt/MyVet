package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.BranchRevenue;
import com.vetos.modules.billing.application.dto.MonthlyRevenue;
import com.vetos.modules.billing.application.dto.RevenueSummary;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.modules.tenant.domain.BranchLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @docs/requirements.md 4.12 "Raporlama & Analitik" -- dashboard'daki
 * "Isletme ozeti" sekmesi eskiden sabit/uydurma verilerle doluydu, bu use-case
 * gercek Invoice kayitlarindan hesaplanan aylik ciro trendi ve sube bazli
 * ciro kirilimi uretir.
 */
@Service
@RequiredArgsConstructor
public class GetRevenueSummaryUseCase {

    private static final Set<InvoiceStatus> REVENUE_STATUSES = EnumSet.of(
        InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID
    );
    private static final int TREND_MONTHS = 6;

    private final InvoiceRepository invoiceRepository;
    private final BranchLookupPort branchLookupPort;

    @Transactional(readOnly = true)
    public RevenueSummary execute(UUID tenantId) {
        List<Invoice> revenueInvoices = invoiceRepository.findByTenantId(tenantId).stream()
            .filter(inv -> REVENUE_STATUSES.contains(inv.getStatus()) && inv.getIssuedAt() != null)
            .toList();

        YearMonth currentMonth = YearMonth.now();
        List<YearMonth> months = IntStream.rangeClosed(0, TREND_MONTHS - 1)
            .mapToObj(i -> currentMonth.minusMonths(TREND_MONTHS - 1L - i))
            .toList();

        Map<YearMonth, BigDecimal> revenueByMonth = revenueInvoices.stream()
            .collect(Collectors.groupingBy(
                inv -> YearMonth.from(inv.getIssuedAt().atZone(ZoneOffset.UTC)),
                Collectors.reducing(BigDecimal.ZERO, Invoice::getTotalAmount, BigDecimal::add)
            ));

        List<MonthlyRevenue> trend = months.stream()
            .map(m -> new MonthlyRevenue(m.toString(), revenueByMonth.getOrDefault(m, BigDecimal.ZERO)))
            .toList();

        Map<UUID, BigDecimal> revenueByBranch = revenueInvoices.stream()
            .filter(inv -> YearMonth.from(inv.getIssuedAt().atZone(ZoneOffset.UTC)).equals(currentMonth))
            .collect(Collectors.groupingBy(Invoice::getBranchId, Collectors.reducing(BigDecimal.ZERO, Invoice::getTotalAmount, BigDecimal::add)));

        List<BranchRevenue> branchBreakdown = revenueByBranch.entrySet().stream()
            .map(e -> new BranchRevenue(e.getKey(), branchLookupPort.findSummaryById(e.getKey()).name(), e.getValue()))
            .sorted(Comparator.comparing(BranchRevenue::revenue).reversed())
            .toList();

        BigDecimal currentMonthRevenue = revenueByMonth.getOrDefault(currentMonth, BigDecimal.ZERO);
        BigDecimal previousMonthRevenue = revenueByMonth.getOrDefault(currentMonth.minusMonths(1), BigDecimal.ZERO);

        return new RevenueSummary(trend, branchBreakdown, currentMonthRevenue, previousMonthRevenue);
    }
}
