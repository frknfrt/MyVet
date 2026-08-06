package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.ProductSalesLine;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceLine;
import com.vetos.modules.billing.domain.InvoiceLineRepository;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @docs/requirements.md 4.12 "Cok boyutlu gelismis raporlar" -- urun/hizmet
 * bazli detayli rapor seti. Fatura kalemlerini (InvoiceLine) aciklamaya gore
 * gruplayip toplam adet/ciro hesaplar.
 */
@Service
@RequiredArgsConstructor
public class GetProductSalesReportUseCase {

    private static final Set<InvoiceStatus> DEFAULT_STATUSES = EnumSet.of(
        InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID
    );

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;

    @Transactional(readOnly = true)
    public List<ProductSalesLine> execute(UUID tenantId, Instant from, Instant to, UUID branchId, InvoiceStatus status) {
        List<Invoice> invoices = invoiceRepository.findByTenantId(tenantId).stream()
            .filter(inv -> inv.getIssuedAt() != null)
            .filter(inv -> !inv.getIssuedAt().isBefore(from) && inv.getIssuedAt().isBefore(to))
            .filter(inv -> branchId == null || inv.getBranchId().equals(branchId))
            .filter(inv -> status != null ? inv.getStatus() == status : DEFAULT_STATUSES.contains(inv.getStatus()))
            .toList();

        Map<String, ProductAgg> byDescription = new LinkedHashMap<>();
        for (Invoice invoice : invoices) {
            for (InvoiceLine line : invoiceLineRepository.findByInvoiceId(invoice.getId())) {
                ProductAgg agg = byDescription.computeIfAbsent(line.getDescription(), d -> new ProductAgg());
                agg.quantity += line.getQuantity();
                agg.revenue = agg.revenue.add(line.getLineTotal());
            }
        }

        return byDescription.entrySet().stream()
            .map(e -> new ProductSalesLine(e.getKey(), e.getValue().quantity, e.getValue().revenue))
            .sorted(Comparator.comparing(ProductSalesLine::totalRevenue).reversed())
            .toList();
    }

    private static class ProductAgg {
        int quantity;
        BigDecimal revenue = BigDecimal.ZERO;
    }
}
