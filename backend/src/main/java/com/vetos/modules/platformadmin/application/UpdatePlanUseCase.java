package com.vetos.modules.platformadmin.application;

import com.vetos.platform.cache.CacheNames;
import org.springframework.cache.annotation.CacheEvict;
import com.vetos.modules.platformadmin.application.dto.UpdatePlanCommand;
import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.exception.PlanNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdatePlanUseCase {

    private final PlanRepository planRepository;
    private final RecordAuditLogUseCase recordAuditLogUseCase;

    @CacheEvict(cacheNames = CacheNames.PLAN_BY_CODE, allEntries = true)
    @Transactional
    public void execute(UpdatePlanCommand command, UUID platformAdminId, String platformAdminEmail) {
        Plan plan = planRepository.findById(command.planId())
            .orElseThrow(() -> new PlanNotFoundException(command.planId()));

        plan.updateDetails(
            command.name(),
            command.monthlyPrice(),
            command.annualPrice(),
            command.description(),
            command.badge(),
            command.imageUrl(),
            command.features(),
            command.enabledFeatures()
        );
        plan.updateQuotas(command.monthlyAiQuota(), command.monthlyMessageQuota());
        if (command.active()) {
            plan.activate();
        } else {
            plan.deactivate();
        }
        planRepository.save(plan);
        recordAuditLogUseCase.execute(
            platformAdminId, platformAdminEmail, AuditAction.PLAN_UPDATED, "PLAN", command.planId(), command.name()
        );
    }
}
