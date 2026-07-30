package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.OwnerBalance;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.modules.billing.domain.Payment;
import com.vetos.modules.billing.domain.PaymentRepository;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @docs/implementation-plan.md Modul 5: "Borc listesi / cari hesap -- sahip
 * bazli konsolide bakiye". Sadece fatura bazli degil, her sahip icin acik
 * (ISSUED/PARTIALLY_PAID) faturalarin odenmemis kismi toplanir.
 */
@Service
@RequiredArgsConstructor
public class GetOwnerBalancesUseCase {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final OwnerLookupPort ownerLookupPort;

    @Transactional(readOnly = true)
    public List<OwnerBalance> execute(UUID tenantId) {
        List<Invoice> openInvoices = invoiceRepository.findByTenantId(tenantId).stream()
            .filter(i -> i.getStatus() == InvoiceStatus.ISSUED || i.getStatus() == InvoiceStatus.PARTIALLY_PAID)
            .toList();

        Map<UUID, BigDecimal> outstandingByOwner = openInvoices.stream()
            .collect(Collectors.groupingBy(
                Invoice::getOwnerId,
                Collectors.reducing(BigDecimal.ZERO, this::outstandingAmountOf, BigDecimal::add)
            ));

        return outstandingByOwner.entrySet().stream()
            .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
            .map(entry -> {
                var owner = ownerLookupPort.findSummaryById(entry.getKey());
                return new OwnerBalance(owner.id(), owner.fullName(), owner.phone(), entry.getValue());
            })
            .sorted(Comparator.comparing(OwnerBalance::outstandingBalance).reversed())
            .toList();
    }

    private BigDecimal outstandingAmountOf(Invoice invoice) {
        BigDecimal paid = paymentRepository.findByInvoiceId(invoice.getId()).stream()
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return invoice.getTotalAmount().subtract(paid);
    }
}
