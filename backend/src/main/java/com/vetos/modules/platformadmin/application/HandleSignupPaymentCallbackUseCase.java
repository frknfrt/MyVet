package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.modules.platformadmin.domain.CheckoutResult;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformPaymentMethod;
import com.vetos.modules.platformadmin.domain.TenantSignupRequest;
import com.vetos.modules.platformadmin.domain.TenantSignupRequestRepository;
import com.vetos.modules.platformadmin.domain.exception.PlanNotFoundException;
import com.vetos.modules.platformadmin.domain.exception.TenantSignupRequestNotFoundException;
import com.vetos.modules.tenant.domain.InviteEmailPort;
import com.vetos.modules.tenant.domain.StaffInvite;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantSignupResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * iyzico'nun checkout callback'i sonrasi, conversationId bilinen bir
 * PlatformInvoice'a ait DEGILSE (PublicPaymentCallbackController'daki
 * fallback), bu use-case cagrilir -- vetly.com'da yeni bir kayit odemesi.
 * Idempotent: TenantSignupRequest zaten COMPLETED ise tenant tekrar
 * olusturulmaz.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HandleSignupPaymentCallbackUseCase {

    private final PaymentGatewayPort paymentGatewayPort;
    private final TenantSignupRequestRepository tenantSignupRequestRepository;
    private final PlanRepository planRepository;
    private final TenantAdminPort tenantAdminPort;
    private final InviteEmailPort inviteEmailPort;
    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final RecordPlatformPaymentUseCase recordPlatformPaymentUseCase;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public boolean execute(String token, LocalDate today) {
        CheckoutResult result = paymentGatewayPort.retrieveCheckoutResult(token);
        if (!result.success()) {
            log.info("iyzico kayit odemesi basarisiz: conversationId={}", result.conversationId());
            return false;
        }

        UUID requestId = UUID.fromString(result.conversationId());
        TenantSignupRequest request = tenantSignupRequestRepository.findById(requestId)
            .orElseThrow(() -> new TenantSignupRequestNotFoundException(requestId));

        if (request.isCompleted()) {
            log.info("iyzico kayit callback'i tekrarlandi, kayit zaten tamamlanmis: requestId={}", requestId);
            return true;
        }

        Plan plan = planRepository.findByCode(request.getPlanCode())
            .orElseThrow(() -> new PlanNotFoundException(request.getPlanCode()));

        LocalDate renewsAt = today.plusMonths(1);
        TenantSignupResult tenant = tenantAdminPort.createTenantForPaidSignup(
            request.getClinicName(), "-", request.getClinicName(), "-", "-", request.getPlanCode(), renewsAt
        );

        StaffInvite invite = tenantAdminPort.createAdminInviteForPaidSignup(
            tenant.tenantId(), tenant.branchId(), request.getAdminEmail(), request.getAdminFullName()
        );
        String acceptUrl = frontendBaseUrl + "/davet/" + invite.getToken();
        inviteEmailPort.sendInvite(invite, request.getClinicName(), acceptUrl);

        PlatformInvoice invoice = platformInvoiceRepository.save(
            PlatformInvoice.issue(tenant.tenantId(), request.getPlanCode(), plan.getMonthlyPrice(), today, renewsAt, today)
        );
        recordPlatformPaymentUseCase.execute(new RecordPlatformPaymentCommand(
            invoice.getId(), plan.getMonthlyPrice(), PlatformPaymentMethod.CARD_ONLINE,
            today, "iyzico odeme referansi: " + result.paymentId(), null
        ));

        request.complete();
        tenantSignupRequestRepository.save(request);

        return true;
    }
}
