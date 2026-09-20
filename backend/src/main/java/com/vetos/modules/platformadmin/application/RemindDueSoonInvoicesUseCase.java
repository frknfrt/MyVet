package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformBillingEmailPort;
import com.vetos.modules.platformadmin.domain.PlatformBillingSmsPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class RemindDueSoonInvoicesUseCase {

    private static final int REMINDER_DAYS_BEFORE_DUE = 2;

    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final TenantAdminPort tenantAdminPort;
    private final PlatformBillingEmailPort platformBillingEmailPort;
    private final PlatformBillingSmsPort platformBillingSmsPort;

    // REQUIRES_NEW: disi transaction PLATFORM_FATURALAMA_KILIDI'ni tutuyor -- bagimsiz transaction olmazsa,
    // ucden biri patlarsa gunun butun faturalama isi (digerleri dahil) sessizce geri alinir.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(LocalDate today) {
        LocalDate targetDueDate = today.plusDays(REMINDER_DAYS_BEFORE_DUE);
        for (PlatformInvoice invoice : platformInvoiceRepository.findByStatusAndDueDate(PlatformInvoiceStatus.ISSUED, targetDueDate)) {
            TenantAdminOverview overview = tenantAdminPort.getOverview(invoice.getTenantId());
            tenantAdminPort.findBillingContactEmail(invoice.getTenantId())
                .ifPresent(email -> platformBillingEmailPort.sendInvoiceDueSoon(invoice, overview.name(), email));
            tenantAdminPort.findBillingContactPhone(invoice.getTenantId())
                .ifPresent(phone -> platformBillingSmsPort.sendInvoiceDueSoon(invoice, overview.name(), phone));
        }
    }
}
