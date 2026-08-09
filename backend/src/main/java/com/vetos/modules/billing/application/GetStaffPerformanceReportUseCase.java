package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.StaffPerformanceLine;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @docs/requirements.md 4.12 "Cok boyutlu gelismis raporlar" -- hekim bazli
 * performans raporu. Sadece staffUserId atanmis (encounter kaynakli) faturalari
 * kapsar; yatis (boarding) faturalarinda hekim atamasi olmadigindan bu rapora
 * dahil edilmez (bkz. Invoice.createDraftForBoardingStay).
 */
@Service
@RequiredArgsConstructor
public class GetStaffPerformanceReportUseCase {

    private final InvoiceRepository invoiceRepository;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional(readOnly = true)
    public List<StaffPerformanceLine> execute(UUID tenantId, Instant from, Instant to, UUID branchId, InvoiceStatus status) {
        Map<UUID, List<Invoice>> byStaff = invoiceRepository.findByTenantId(tenantId).stream()
            .filter(inv -> inv.getStaffUserId() != null)
            .filter(inv -> inv.getIssuedAt() != null)
            .filter(inv -> !inv.getIssuedAt().isBefore(from) && inv.getIssuedAt().isBefore(to))
            .filter(inv -> branchId == null || inv.getBranchId().equals(branchId))
            .filter(inv -> status == null || inv.getStatus() == status)
            .collect(Collectors.groupingBy(Invoice::getStaffUserId));

        return byStaff.entrySet().stream()
            .map(e -> toLine(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(StaffPerformanceLine::totalRevenue).reversed())
            .toList();
    }

    private StaffPerformanceLine toLine(UUID staffUserId, List<Invoice> invoices) {
        var staff = staffUserLookupPort.findSummaryById(staffUserId);
        BigDecimal total = invoices.stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = total.divide(BigDecimal.valueOf(invoices.size()), 2, RoundingMode.HALF_UP);
        return new StaffPerformanceLine(staffUserId, staff.fullName(), invoices.size(), total, avg);
    }
}
