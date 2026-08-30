package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.CheckoutSession;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.TenantSignupRequest;
import com.vetos.modules.platformadmin.domain.TenantSignupRequestRepository;
import com.vetos.modules.platformadmin.domain.exception.PlanNotFoundException;
import com.vetos.modules.platformadmin.domain.exception.SignupEmailAlreadyRegisteredConflictException;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InitiateSignupCheckoutUseCase {

    private final PlanRepository planRepository;
    private final TenantSignupRequestRepository tenantSignupRequestRepository;
    private final TenantAdminPort tenantAdminPort;
    private final PaymentGatewayPort paymentGatewayPort;

    @Transactional
    public CheckoutSession execute(String clinicName, String adminFullName, String adminEmail, String phone, String planCode) {
        Plan plan = planRepository.findByCode(planCode)
            .filter(Plan::isActive)
            .orElseThrow(() -> new PlanNotFoundException(planCode));

        if (tenantAdminPort.isEmailRegistered(adminEmail)) {
            throw new SignupEmailAlreadyRegisteredConflictException(adminEmail);
        }

        TenantSignupRequest request = tenantSignupRequestRepository.save(
            TenantSignupRequest.create(clinicName, adminFullName, adminEmail, phone, planCode)
        );

        return paymentGatewayPort.initializeCheckout(
            request.getId().toString(), plan.getMonthlyPrice(), adminFullName, adminEmail
        );
    }
}
