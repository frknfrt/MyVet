package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.CheckoutSession;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceInvalidTransitionException;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InitiateInvoiceCheckoutUseCase {

    private static final String FALLBACK_BUYER_EMAIL = "destek@myvet.app";

    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final TenantAdminPort tenantAdminPort;
    private final PaymentGatewayPort paymentGatewayPort;

    @Transactional(readOnly = true)
    public CheckoutSession execute(UUID tenantId, UUID invoiceId) {
        PlatformInvoice invoice = platformInvoiceRepository.findById(invoiceId)
            .filter(i -> i.getTenantId().equals(tenantId))
            .orElseThrow(() -> new PlatformInvoiceNotFoundException(invoiceId));

        if (invoice.getStatus() != PlatformInvoiceStatus.ISSUED && invoice.getStatus() != PlatformInvoiceStatus.OVERDUE) {
            throw new PlatformInvoiceInvalidTransitionException(invoice.getStatus(), PlatformInvoiceStatus.PAID);
        }

        TenantAdminOverview overview = tenantAdminPort.getOverview(tenantId);
        String buyerEmail = tenantAdminPort.findBillingContactEmail(tenantId).orElse(FALLBACK_BUYER_EMAIL);

        return paymentGatewayPort.initializeCheckout(invoice.getId().toString(), invoice.getAmount(), overview.name(), buyerEmail);
    }
}
