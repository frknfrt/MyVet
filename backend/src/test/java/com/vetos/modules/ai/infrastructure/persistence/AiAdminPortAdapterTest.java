package com.vetos.modules.ai.infrastructure.persistence;

import com.vetos.modules.ai.domain.AccuracyFeedback;
import com.vetos.modules.ai.domain.AiJob;
import com.vetos.modules.ai.domain.AiJobDecision;
import com.vetos.modules.ai.domain.AiTaskType;
import com.vetos.modules.ai.domain.AiTenantUsage;
import com.vetos.modules.ai.domain.DecisionStatus;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Koprulme kurali (tasarim dokumani S5, bkz. TenantAdminPortAdapterTest):
 * AiJobDecision @TenantId'li, platform admin istegi hicbir kiraciya
 * baglanmamis context'te calisir -- adapter her kiraci icin TenantContext'i
 * elle kurup okumali, finally'de temizlemeli. Gercek Hibernate yok, mock
 * repository'ler icinde TenantContext'in degeri yakalanir.
 */
@ExtendWith(MockitoExtension.class)
class AiAdminPortAdapterTest {

    @Mock private AiJobJpaRepository aiJobJpaRepository;
    @Mock private AiJobDecisionJpaRepository aiJobDecisionJpaRepository;

    private AiAdminPortAdapter adapter() {
        return new AiAdminPortAdapter(aiJobJpaRepository, aiJobDecisionJpaRepository);
    }

    private static AiJob job(UUID tenantId, AiTaskType taskType) {
        return AiJob.create(tenantId, taskType, UUID.randomUUID(), "metin", "claude-sonnet", "v1", UUID.randomUUID());
    }

    @Test
    void listUsageByTenant_setsTenantContext_perTenant_andClearsIt() {
        UUID tenantId = UUID.randomUUID();
        List<AiJob> jobs = List.of(job(tenantId, AiTaskType.DIAGNOSIS_SUGGESTION));
        when(aiJobJpaRepository.findAll()).thenReturn(jobs);

        AtomicReference<UUID> seen = new AtomicReference<>();
        when(aiJobDecisionJpaRepository.findAll()).thenAnswer(invocation -> {
            seen.set(TenantContext.current());
            return List.of();
        });

        adapter().listUsageByTenant();

        assertThat(seen.get()).isEqualTo(tenantId);
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void listUsageByTenant_countsJobsAndDecisions_correctly() {
        UUID tenantId = UUID.randomUUID();
        List<AiJob> jobs = List.of(
            job(tenantId, AiTaskType.DIAGNOSIS_SUGGESTION),
            job(tenantId, AiTaskType.DIAGNOSIS_SUGGESTION),
            job(tenantId, AiTaskType.TREATMENT_RECOMMENDATION),
            job(tenantId, AiTaskType.TREATMENT_RECOMMENDATION)
        );
        when(aiJobJpaRepository.findAll()).thenReturn(jobs);

        AiJobDecision accepted = AiJobDecision.createPending(tenantId, UUID.randomUUID());
        accepted.decide(DecisionStatus.ACCEPTED_AS_IS, null, UUID.randomUUID());
        accepted.recordFeedback(AccuracyFeedback.ACCURATE);

        AiJobDecision edited = AiJobDecision.createPending(tenantId, UUID.randomUUID());
        edited.decide(DecisionStatus.ACCEPTED_WITH_EDITS, "duzenlenmis", UUID.randomUUID());

        AiJobDecision rejected = AiJobDecision.createPending(tenantId, UUID.randomUUID());
        rejected.decide(DecisionStatus.REJECTED, null, UUID.randomUUID());
        rejected.recordFeedback(AccuracyFeedback.INACCURATE);

        when(aiJobDecisionJpaRepository.findAll()).thenReturn(List.of(accepted, edited, rejected));

        List<AiTenantUsage> result = adapter().listUsageByTenant();

        assertThat(result).hasSize(1);
        AiTenantUsage usage = result.get(0);
        assertThat(usage.tenantId()).isEqualTo(tenantId);
        assertThat(usage.totalJobs()).isEqualTo(4);
        assertThat(usage.diagnosisJobs()).isEqualTo(2);
        assertThat(usage.treatmentJobs()).isEqualTo(2);
        assertThat(usage.acceptedAsIs()).isEqualTo(1);
        assertThat(usage.acceptedWithEdits()).isEqualTo(1);
        assertThat(usage.rejected()).isEqualTo(1);
        assertThat(usage.noDecisionYet()).isEqualTo(1); // 4 is - 3 karar = 1 karar verilmemis
        assertThat(usage.accurateFeedback()).isEqualTo(1);
        assertThat(usage.inaccurateFeedback()).isEqualTo(1);
    }

    @Test
    void listUsageByTenant_groupsByTenant_andSortsByTotalJobsDescending() {
        UUID smallTenant = UUID.randomUUID();
        UUID bigTenant = UUID.randomUUID();
        List<AiJob> jobs = List.of(
            job(smallTenant, AiTaskType.DIAGNOSIS_SUGGESTION),
            job(bigTenant, AiTaskType.DIAGNOSIS_SUGGESTION),
            job(bigTenant, AiTaskType.TREATMENT_RECOMMENDATION)
        );
        when(aiJobJpaRepository.findAll()).thenReturn(jobs);
        when(aiJobDecisionJpaRepository.findAll()).thenReturn(List.of());

        List<AiTenantUsage> result = adapter().listUsageByTenant();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).tenantId()).isEqualTo(bigTenant);
        assertThat(result.get(0).totalJobs()).isEqualTo(2);
        assertThat(result.get(1).tenantId()).isEqualTo(smallTenant);
        assertThat(result.get(1).totalJobs()).isEqualTo(1);
    }

    @Test
    void listUsageByTenant_returnsEmptyList_whenNoJobsExist() {
        when(aiJobJpaRepository.findAll()).thenReturn(List.of());

        List<AiTenantUsage> result = adapter().listUsageByTenant();

        assertThat(result).isEmpty();
    }
}
