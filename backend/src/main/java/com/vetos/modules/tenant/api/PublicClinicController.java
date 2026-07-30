package com.vetos.modules.tenant.api;

import com.vetos.modules.tenant.api.dto.BranchOverviewResponse;
import com.vetos.modules.tenant.application.GetCurrentBranchUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * @docs/implementation-plan.md Modul 8: klinik tanitim sayfasi + online
 * randevu widget'i icin kimlik dogrulama gerektirmeyen uc noktalar.
 * SecurityConfig'de /api/v1/public/** zaten permitAll.
 */
@RestController
@RequestMapping("/api/v1/public/clinics")
@RequiredArgsConstructor
public class PublicClinicController {

    private final GetCurrentBranchUseCase getCurrentBranchUseCase;

    @GetMapping("/{branchId}")
    public BranchOverviewResponse get(@PathVariable UUID branchId) {
        return BranchOverviewResponse.from(getCurrentBranchUseCase.execute(branchId));
    }
}
