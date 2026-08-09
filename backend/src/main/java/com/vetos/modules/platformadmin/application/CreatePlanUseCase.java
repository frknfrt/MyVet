package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.CreatePlanCommand;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.exception.PlanCodeAlreadyExistsConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatePlanUseCase {

    private final PlanRepository planRepository;

    @Transactional
    public UUID execute(CreatePlanCommand command) {
        if (planRepository.existsByCode(command.code())) {
            throw new PlanCodeAlreadyExistsConflictException(command.code());
        }
        Plan plan = Plan.create(command.code(), command.name(), command.monthlyPrice());
        return planRepository.save(plan).getId();
    }
}
