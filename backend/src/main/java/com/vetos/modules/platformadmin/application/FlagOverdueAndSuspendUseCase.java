package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformBillingEmailPort;
import com.vetos.modules.platformadmin.domain.PlatformBillingSmsPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class FlagOverdueAndSuspendUseCase {

    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final TenantAdminPort tenantAdminPort;
    private final PlatformBillingEmailPort platformBillingEmailPort;
    private final PlatformBillingSmsPort platformBillingSmsPort;

    // REQUIRES_NEW: disi transaction PLATFORM_FATURALAMA_KILIDI'ni tutuyor -- bagimsiz transaction olmazsa,
    // ucden biri patlarsa gunun butun faturalama isi (digerleri dahil) sessizce geri alinir.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(LocalDate today) {
        for (PlatformInvoice invoice : platformInvoiceRepository.findByStatusAndDueDateBefore(PlatformInvoiceStatus.ISSUED, today)) {
            invoice.markOverdue();
            platformInvoiceRepository.save(invoice);

            tenantAdminPort.updateBillingStatus(invoice.getTenantId(), BillingStatus.PAST_DUE);
            tenantAdminPort.suspend(invoice.getTenantId());

            TenantAdminOverview overview = tenantAdminPort.getOverview(invoice.getTenantId());
            tenantAdminPort.findBillingContactEmail(invoice.getTenantId())
                .ifPresent(email -> platformBillingEmailPort.sendTenantSuspended(overview.name(), email));
            tenantAdminPort.findBillingContactPhone(invoice.getTenantId())
                .ifPresent(phone -> platformBillingSmsPort.sendTenantSuspended(overview.name(), phone));
        }
    }
}
