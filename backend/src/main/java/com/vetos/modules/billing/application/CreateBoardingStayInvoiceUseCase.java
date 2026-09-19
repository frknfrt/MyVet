package com.vetos.modules.billing.application;

import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceLine;
import com.vetos.modules.billing.domain.InvoiceLineRepository;
import com.vetos.modules.billing.domain.InvoiceLineSource;
import com.vetos.modules.billing.domain.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * boarding modulunun BoardingStayCreatedEvent'ini dinleyerek tetiklenir --
 * AutoCaptureEncounterChargeUseCase ile ayni desen (@docs/architecture.md Bolum 4).
 * Konaklama kaydi olusturuldugunda otomatik DRAFT fatura + baslangic kalemi acilir;
 * resepsiyonist tutari/kalemleri Finans ekranindan duzenleyebilir.
 */
@Service
@RequiredArgsConstructor
public class CreateBoardingStayInvoiceUseCase {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;

    @Transactional
    public UUID execute(
        UUID tenantId, UUID branchId, UUID ownerId, UUID boardingStayId,
        String roomLabel, BigDecimal dailyRate, LocalDate checkInDate, LocalDate expectedCheckOutDate
    ) {
        Invoice invoice = invoiceRepository.save(
            Invoice.createDraftForBoardingStay(tenantId, branchId, ownerId, boardingStayId)
        );

        long nights = expectedCheckOutDate != null
            ? Math.max(1, ChronoUnit.DAYS.between(checkInDate, expectedCheckOutDate))
            : 1;
        BigDecimal unitPrice = dailyRate != null ? dailyRate : BigDecimal.ZERO;

        InvoiceLine line = invoiceLineRepository.save(InvoiceLine.create(
            invoice.getTenantId(), invoice.getId(), "Konaklama Bedeli (" + roomLabel + ")", (int) nights, unitPrice,
            null, null, InvoiceLineSource.AUTO_CHARGE_CAPTURE
        ));
        invoice.recalculateTotal(line.getLineTotal());
        invoiceRepository.save(invoice);

        return invoice.getId();
    }
}
