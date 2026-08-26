package com.vetos.modules.billing.application;

import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * AutoCaptureEncounterChargeUseCase/CreateBoardingStayInvoiceUseCase ile ayni
 * desen (bos bir DRAFT fatura acar) -- farki bir encounter/konaklamaya degil,
 * dogrudan resepsiyonistin secilen bir sahibe manuel actigi faturaya karsilik
 * gelmesi. Kalemler mevcut POST /invoices/{id}/lines ile eklenir.
 */
@Service
@RequiredArgsConstructor
public class CreateManualInvoiceUseCase {

    private final InvoiceRepository invoiceRepository;

    @Transactional
    public UUID execute(UUID branchId, UUID ownerId, UUID staffUserId) {
        Invoice invoice = invoiceRepository.save(
            Invoice.createDraft(TenantContext.current(), branchId, ownerId, null, staffUserId)
        );
        return invoice.getId();
    }
}
