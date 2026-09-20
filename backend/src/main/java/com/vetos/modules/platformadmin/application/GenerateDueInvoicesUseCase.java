package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.PlatformBillingEmailPort;
import com.vetos.modules.platformadmin.domain.PlatformBillingSmsPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.tenant.domain.BillableSubscription;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenerateDueInvoicesUseCase {

    private final TenantAdminPort tenantAdminPort;
    private final PlanRepository planRepository;
    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final PlatformBillingEmailPort platformBillingEmailPort;
    private final PlatformBillingSmsPort platformBillingSmsPort;

    // REQUIRES_NEW: disi transaction PLATFORM_FATURALAMA_KILIDI'ni tutuyor -- bagimsiz transaction olmazsa,
    // ucden biri patlarsa gunun butun faturalama isi (digerleri dahil) sessizce geri alinir.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(LocalDate today) {
        for (BillableSubscription subscription : tenantAdminPort.listSubscriptionsDueOnOrBefore(today)) {
            if (platformInvoiceRepository.findByTenantIdAndPeriodStart(subscription.tenantId(), subscription.renewsAt()).isPresent()) {
                continue;
            }

            Plan plan = planRepository.findByCode(subscription.planCode()).orElse(null);
            if (plan == null) {
                log.warn("Fatura uretilemedi, plan bulunamadi: tenantId={}, planCode={}", subscription.tenantId(), subscription.planCode());
                continue;
            }

            LocalDate periodStart = subscription.renewsAt();
            LocalDate periodEnd = periodStart.plusMonths(1);
            PlatformInvoice invoice = PlatformInvoice.issue(
                subscription.tenantId(), subscription.planCode(), plan.getMonthlyPrice(), periodStart, periodEnd, today
            );
            platformInvoiceRepository.save(invoice);
            tenantAdminPort.advanceRenewal(subscription.tenantId(), periodEnd);

            notifyInvoiceIssued(invoice, subscription.tenantId());
        }
    }

    private void notifyInvoiceIssued(PlatformInvoice invoice, UUID tenantId) {
        TenantAdminOverview overview = tenantAdminPort.getOverview(tenantId);
        tenantAdminPort.findBillingContactEmail(tenantId)
            .ifPresent(email -> platformBillingEmailPort.sendInvoiceIssued(invoice, overview.name(), email));
        tenantAdminPort.findBillingContactPhone(tenantId)
            .ifPresent(phone -> platformBillingSmsPort.sendInvoiceIssued(invoice, overview.name(), phone));
    }
}
