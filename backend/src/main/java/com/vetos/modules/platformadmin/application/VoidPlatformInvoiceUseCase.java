package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoidPlatformInvoiceUseCase {

    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final TenantBillingReconciler tenantBillingReconciler;

    @Transactional
    public void execute(UUID invoiceId) {
        PlatformInvoice invoice = platformInvoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new PlatformInvoiceNotFoundException(invoiceId));

        boolean wasOverdue = invoice.getStatus() == PlatformInvoiceStatus.OVERDUE;

        invoice.voidInvoice();
        platformInvoiceRepository.save(invoice);

        if (wasOverdue) {
            tenantBillingReconciler.reconcileAfterInvoiceResolved(invoice.getTenantId(), invoice.getId());
        }
    }
}
