package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.UpdatePlanCommand;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.exception.PlanNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdatePlanUseCase {

    private final PlanRepository planRepository;

    @Transactional
    public void execute(UpdatePlanCommand command) {
        Plan plan = planRepository.findById(command.planId())
            .orElseThrow(() -> new PlanNotFoundException(command.planId()));

        plan.updateDetails(
            command.name(),
            command.monthlyPrice(),
            command.annualPrice(),
            command.description(),
            command.badge(),
            command.imageUrl(),
            command.features()
        );
        if (command.active()) {
            plan.activate();
        } else {
            plan.deactivate();
        }
        planRepository.save(plan);
    }
}
