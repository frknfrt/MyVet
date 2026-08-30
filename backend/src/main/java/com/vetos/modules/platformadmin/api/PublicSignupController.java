package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.CheckoutSessionResponse;
import com.vetos.modules.platformadmin.api.dto.InitiateSignupCheckoutRequest;
import com.vetos.modules.platformadmin.api.dto.PlanResponse;
import com.vetos.modules.platformadmin.application.InitiateSignupCheckoutUseCase;
import com.vetos.modules.platformadmin.application.ListPlansUseCase;
import com.vetos.modules.platformadmin.domain.CheckoutSession;
import com.vetos.modules.platformadmin.domain.Plan;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * vetly.com'daki self-servis kayit akisi icin kimlik dogrulama gerektirmeyen
 * uc noktalar -- SecurityConfig'de /api/v1/public/** zaten permitAll
 * (PublicClinicController ile ayni desen).
 */
@RestController
@RequestMapping("/api/v1/public/signup")
@RequiredArgsConstructor
public class PublicSignupController {

    private final ListPlansUseCase listPlansUseCase;
    private final InitiateSignupCheckoutUseCase initiateSignupCheckoutUseCase;

    @GetMapping("/plans")
    public List<PlanResponse> plans() {
        return listPlansUseCase.execute().stream()
            .filter(Plan::isActive)
            .map(PlanResponse::from)
            .toList();
    }

    @PostMapping("/checkout")
    public CheckoutSessionResponse checkout(@RequestBody @Valid InitiateSignupCheckoutRequest request) {
        CheckoutSession session = initiateSignupCheckoutUseCase.execute(
            request.clinicName(), request.adminFullName(), request.adminEmail(), request.phone(), request.planCode()
        );
        return new CheckoutSessionResponse(session.checkoutFormUrl());
    }
}
