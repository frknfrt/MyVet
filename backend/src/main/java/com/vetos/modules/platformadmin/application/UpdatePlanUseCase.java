package com.vetos.modules.platformadmin.application;

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
