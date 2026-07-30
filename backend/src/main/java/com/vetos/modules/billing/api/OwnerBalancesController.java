package com.vetos.modules.billing.api;

import com.vetos.modules.billing.api.dto.OwnerBalanceResponse;
import com.vetos.modules.billing.application.GetOwnerBalancesUseCase;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** @docs/implementation-plan.md Modul 5: "Borc listesi / cari hesap". */
@RestController
@RequestMapping("/api/v1/owner-balances")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
public class OwnerBalancesController {

    private final GetOwnerBalancesUseCase getOwnerBalancesUseCase;

    @GetMapping
    public List<OwnerBalanceResponse> list() {
        return getOwnerBalancesUseCase.execute(TenantContext.current()).stream().map(OwnerBalanceResponse::from).toList();
    }
}
