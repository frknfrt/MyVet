package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.BranchComparisonLine;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.modules.billing.domain.Payment;
import com.vetos.modules.billing.domain.PaymentRepository;
import com.vetos.modules.tenant.domain.BranchLookupPort;
import com.vetos.modules.tenant.domain.BranchSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @docs/requirements.md 4.12 -- cok subeli klinikler icin konsolide karsilastirma
 * paneli. GetRevenueSummaryUseCase'in aylik/tek-ay sube kirilimindan farkli
 * olarak tarih araligi filtrelenebilir ve faturasi olmayan subeler de (0 satirla)
 * listeye dahil edilir (gercek "tum subeler" gorunumu).
 */
@Service
@RequiredArgsConstructor
public class GetBranchComparisonReportUseCase {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final BranchLookupPort branchLookupPort;

    @Transactional(readOnly = true)
    public List<BranchComparisonLine> execute(UUID tenantId, Instant from, Instant to, InvoiceStatus status) {
        List<Invoice> invoices = invoiceRepository.findByTenantId(tenantId).stream()
            .filter(inv -> inv.getIssuedAt() != null)
            .filter(inv -> !inv.getIssuedAt().isBefore(from) && inv.getIssuedAt().isBefore(to))
            .filter(inv -> status == null || inv.getStatus() == status)
            .toList();

        Map<UUID, List<Invoice>> byBranch = invoices.stream().collect(Collectors.groupingBy(Invoice::getBranchId));

        List<BranchSummary> allBranches = branchLookupPort.findAllByTenantId(tenantId);

        return allBranches.stream()
            .map(b -> toLine(b, byBranch.getOrDefault(b.id(), List.of())))
            .sorted(Comparator.comparing(BranchComparisonLine::totalRevenue).reversed())
            .toList();
    }

    private BranchComparisonLine toLine(BranchSummary branch, List<Invoice> invoices) {
        BigDecimal total = invoices.stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = invoices.stream()
            .flatMap(inv -> paymentRepository.findByInvoiceId(inv.getId()).stream())
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BranchComparisonLine(branch.id(), branch.name(), invoices.size(), total, paid);
    }
}
