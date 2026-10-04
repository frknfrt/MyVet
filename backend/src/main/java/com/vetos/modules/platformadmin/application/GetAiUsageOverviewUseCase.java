package com.vetos.modules.platformadmin.application;

import com.vetos.modules.ai.domain.AiAdminPort;
import com.vetos.modules.ai.domain.AiTenantUsage;
import com.vetos.modules.platformadmin.application.dto.AiUsageByTenant;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Platform admin "AI Kullanım" paneli -- hangi kiracinin AI (tani/tedavi
 * onerisi) ozelligini ne kadar kullandigini, hekimlerin onerileri ne
 * oranda kabul/red ettigini ve dogruluk geri bildirimini tek ekranda
 * gorebilmek icin. AiAdminPort'tan gelen ham sayimlari TenantAdminPort
 * uzerinden kiraci adiyla zenginlestirir.
 */
@Service
@RequiredArgsConstructor
public class GetAiUsageOverviewUseCase {

    private final AiAdminPort aiAdminPort;
    private final TenantAdminPort tenantAdminPort;

    @Transactional(readOnly = true)
    public List<AiUsageByTenant> execute() {
        List<AiTenantUsage> usages = aiAdminPort.listUsageByTenant();
        Map<UUID, String> tenantNames = tenantAdminPort.listAll().stream()
            .collect(Collectors.toMap(TenantAdminOverview::tenantId, TenantAdminOverview::name, (a, b) -> a));

        return usages.stream()
            .map(u -> new AiUsageByTenant(
                u.tenantId(), tenantNames.getOrDefault(u.tenantId(), "Silinmiş Kiracı"),
                u.totalJobs(), u.diagnosisJobs(), u.treatmentJobs(),
                u.acceptedAsIs(), u.acceptedWithEdits(), u.rejected(), u.noDecisionYet(),
                u.accurateFeedback(), u.inaccurateFeedback(), u.lastUsedAt()
            ))
            .toList();
    }
}
