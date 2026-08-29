package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformPayment;
import com.vetos.modules.platformadmin.domain.PlatformPaymentRepository;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordPlatformPaymentUseCase {

    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final PlatformPaymentRepository platformPaymentRepository;
    private final TenantBillingReconciler tenantBillingReconciler;

    @Transactional
    public void execute(RecordPlatformPaymentCommand command) {
        PlatformInvoice invoice = platformInvoiceRepository.findById(command.invoiceId())
            .orElseThrow(() -> new PlatformInvoiceNotFoundException(command.invoiceId()));

        invoice.markPaid();
        platformInvoiceRepository.save(invoice);

        platformPaymentRepository.save(PlatformPayment.record(
            invoice.getId(), command.amount(), command.method(), command.paidAt(), command.recordedByAdminId(), command.notes()
        ));

        tenantBillingReconciler.reconcileAfterInvoiceResolved(invoice.getTenantId(), invoice.getId());
    }
}
