package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.TenantSignupRequestResponse;
import com.vetos.modules.platformadmin.application.ListTenantSignupRequestsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Platform admin "Potansiyel Musteriler" paneli (bkz. ListTenantSignupRequestsUseCase).
 */
@RestController
@RequestMapping("/api/v1/platform-admin/signup-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class TenantSignupRequestsController {

    private final ListTenantSignupRequestsUseCase listTenantSignupRequestsUseCase;

    @GetMapping
    public List<TenantSignupRequestResponse> list() {
        return listTenantSignupRequestsUseCase.execute().stream().map(TenantSignupRequestResponse::from).toList();
    }
}
