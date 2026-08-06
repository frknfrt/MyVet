package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.RevenueReportLine;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.modules.billing.domain.Payment;
import com.vetos.modules.billing.domain.PaymentRepository;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.tenant.domain.BranchLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * @docs/requirements.md 4.12 "Cok boyutlu gelismis raporlar" -- filtrelenebilir,
 * disa aktarilabilir ciro raporu. Sadece kesilmis (issuedAt dolu) faturalari
 * kapsar; DRAFT faturalar henuz gerceklesmemis geliri temsil etmez.
 */
@Service
@RequiredArgsConstructor
public class GetRevenueReportUseCase {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final OwnerLookupPort ownerLookupPort;
    private final BranchLookupPort branchLookupPort;

    @Transactional(readOnly = true)
    public List<RevenueReportLine> execute(UUID tenantId, Instant from, Instant to, UUID branchId, InvoiceStatus status) {
        return invoiceRepository.findByTenantId(tenantId).stream()
            .filter(inv -> inv.getIssuedAt() != null)
            .filter(inv -> !inv.getIssuedAt().isBefore(from) && inv.getIssuedAt().isBefore(to))
            .filter(inv -> branchId == null || inv.getBranchId().equals(branchId))
            .filter(inv -> status == null || inv.getStatus() == status)
            .sorted(Comparator.comparing(Invoice::getIssuedAt).reversed())
            .map(this::toLine)
            .toList();
    }

    private RevenueReportLine toLine(Invoice invoice) {
        var owner = ownerLookupPort.findSummaryById(invoice.getOwnerId());
        var branch = branchLookupPort.findSummaryById(invoice.getBranchId());
        BigDecimal paid = paymentRepository.findByInvoiceId(invoice.getId()).stream()
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RevenueReportLine(
            invoice.getId(), owner.fullName(), branch.name(), invoice.getIssuedAt(),
            invoice.getStatus(), invoice.getTotalAmount(), paid
        );
    }
}
