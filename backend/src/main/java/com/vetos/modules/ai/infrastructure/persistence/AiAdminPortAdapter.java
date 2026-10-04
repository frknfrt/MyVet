package com.vetos.modules.ai.infrastructure.persistence;

import com.vetos.modules.ai.domain.AiAdminPort;
import com.vetos.modules.ai.domain.AiJob;
import com.vetos.modules.ai.domain.AiJobDecision;
import com.vetos.modules.ai.domain.AiTaskType;
import com.vetos.modules.ai.domain.AiTenantUsage;
import com.vetos.modules.ai.domain.DecisionStatus;
import com.vetos.modules.ai.domain.AccuracyFeedback;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Koprulme kurali (tasarim dokumani S5, bkz. TenantAdminPortAdapter): platform
 * admin istegi hicbir kiraciya baglanmamis context'te calisir. AiJob @TenantId'li
 * DEGIL (bkz. domain), yani tum kiracilarin is hacmini TEK sorguda okuyabiliriz --
 * koprulme gerekmez. AiJobDecision ise @TenantId'li, bu yuzden karar/dogruluk
 * sayimlari icin HER kiraci icin ayri ayri TenantContext kurulup okunur.
 */
@Component
@RequiredArgsConstructor
class AiAdminPortAdapter implements AiAdminPort {

    private final AiJobJpaRepository aiJobJpaRepository;
    private final AiJobDecisionJpaRepository aiJobDecisionJpaRepository;

    @Override
    public List<AiTenantUsage> listUsageByTenant() {
        Map<UUID, List<AiJob>> jobsByTenant = aiJobJpaRepository.findAll().stream()
            .collect(Collectors.groupingBy(AiJob::getTenantId));

        return jobsByTenant.entrySet().stream()
            .map(entry -> toUsage(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingLong(AiTenantUsage::totalJobs).reversed())
            .toList();
    }

    private AiTenantUsage toUsage(UUID tenantId, List<AiJob> jobs) {
        long diagnosisJobs = jobs.stream().filter(j -> j.getTaskType() == AiTaskType.DIAGNOSIS_SUGGESTION).count();
        long treatmentJobs = jobs.stream().filter(j -> j.getTaskType() == AiTaskType.TREATMENT_RECOMMENDATION).count();
        Instant lastUsedAt = jobs.stream().map(AiJob::getCreatedAt).max(Instant::compareTo).orElse(null);

        List<AiJobDecision> decisions;
        TenantContext.set(tenantId);
        try {
            decisions = aiJobDecisionJpaRepository.findAll();
        } finally {
            TenantContext.clear();
        }

        long acceptedAsIs = decisions.stream().filter(d -> d.getDecisionStatus() == DecisionStatus.ACCEPTED_AS_IS).count();
        long acceptedWithEdits = decisions.stream().filter(d -> d.getDecisionStatus() == DecisionStatus.ACCEPTED_WITH_EDITS).count();
        long rejected = decisions.stream().filter(d -> d.getDecisionStatus() == DecisionStatus.REJECTED).count();
        long noDecisionYet = jobs.size() - decisions.size();
        long accurateFeedback = decisions.stream().filter(d -> d.getAccuracyFeedback() == AccuracyFeedback.ACCURATE).count();
        long inaccurateFeedback = decisions.stream().filter(d -> d.getAccuracyFeedback() == AccuracyFeedback.INACCURATE).count();

        return new AiTenantUsage(
            tenantId, jobs.size(), diagnosisJobs, treatmentJobs,
            acceptedAsIs, acceptedWithEdits, rejected, noDecisionYet,
            accurateFeedback, inaccurateFeedback, lastUsedAt
        );
    }
}
