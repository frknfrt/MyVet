package com.vetos.modules.platformadmin.application;

import com.vetos.modules.ai.domain.AiAdminPort;
import com.vetos.modules.ai.domain.AiTenantUsage;
import com.vetos.modules.platformadmin.application.dto.AiUsageByTenant;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAiUsageOverviewUseCaseTest {

    @Mock private AiAdminPort aiAdminPort;
    @Mock private TenantAdminPort tenantAdminPort;

    private GetAiUsageOverviewUseCase useCase() {
        return new GetAiUsageOverviewUseCase(aiAdminPort, tenantAdminPort);
    }

    private TenantAdminOverview tenant(UUID id, String name) {
        return new TenantAdminOverview(
            id, name, "1111111111", TenantStatus.ACTIVE, Instant.now(), "PRO", BillingStatus.ACTIVE,
            LocalDate.now(), LocalDate.now().plusMonths(1), 1, 1, null, null
        );
    }

    @Test
    void should_enrichUsageWithTenantName() {
        UUID tenantId = UUID.randomUUID();
        AiTenantUsage usage = new AiTenantUsage(tenantId, 10, 6, 4, 3, 2, 1, 4, 2, 1, Instant.now());
        when(aiAdminPort.listUsageByTenant()).thenReturn(List.of(usage));
        when(tenantAdminPort.listAll()).thenReturn(List.of(tenant(tenantId, "Pati Vet Klinigi")));

        List<AiUsageByTenant> result = useCase().execute();

        assertThat(result).hasSize(1);
        AiUsageByTenant enriched = result.get(0);
        assertThat(enriched.tenantId()).isEqualTo(tenantId);
        assertThat(enriched.tenantName()).isEqualTo("Pati Vet Klinigi");
        assertThat(enriched.totalJobs()).isEqualTo(10);
        assertThat(enriched.diagnosisJobs()).isEqualTo(6);
        assertThat(enriched.treatmentJobs()).isEqualTo(4);
        assertThat(enriched.acceptedAsIs()).isEqualTo(3);
        assertThat(enriched.acceptedWithEdits()).isEqualTo(2);
        assertThat(enriched.rejected()).isEqualTo(1);
        assertThat(enriched.noDecisionYet()).isEqualTo(4);
        assertThat(enriched.accurateFeedback()).isEqualTo(2);
        assertThat(enriched.inaccurateFeedback()).isEqualTo(1);
    }

    @Test
    void should_fallBackToPlaceholderName_when_tenantNoLongerExists() {
        UUID tenantId = UUID.randomUUID();
        AiTenantUsage usage = new AiTenantUsage(tenantId, 1, 1, 0, 0, 0, 0, 1, 0, 0, Instant.now());
        when(aiAdminPort.listUsageByTenant()).thenReturn(List.of(usage));
        when(tenantAdminPort.listAll()).thenReturn(List.of());

        List<AiUsageByTenant> result = useCase().execute();

        assertThat(result.get(0).tenantName()).isEqualTo("Silinmiş Kiracı");
    }
}
