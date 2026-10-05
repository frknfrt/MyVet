package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.PlatformOverviewResponse;
import com.vetos.modules.platformadmin.application.GetPlatformOverviewUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform admin "Genel Bakis" paneli -- kiraci sayilari, MRR, bu ay
 * tahsilat ve plan dagilimi gibi ust duzey ozet (bkz. GetPlatformOverviewUseCase).
 */
@RestController
@RequestMapping("/api/v1/platform-admin/overview")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformOverviewController {

    private final GetPlatformOverviewUseCase getPlatformOverviewUseCase;

    @GetMapping
    public PlatformOverviewResponse overview() {
        return PlatformOverviewResponse.from(getPlatformOverviewUseCase.execute());
    }
}
