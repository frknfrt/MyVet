package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.AiUsageByTenantResponse;
import com.vetos.modules.platformadmin.application.GetAiUsageOverviewUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Platform admin "AI Kullanım" paneli -- bkz. GetAiUsageOverviewUseCase. */
@RestController
@RequestMapping("/api/v1/platform-admin/ai-usage")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AiUsageController {

    private final GetAiUsageOverviewUseCase getAiUsageOverviewUseCase;

    @GetMapping
    public List<AiUsageByTenantResponse> list() {
        return getAiUsageOverviewUseCase.execute().stream().map(AiUsageByTenantResponse::from).toList();
    }
}
