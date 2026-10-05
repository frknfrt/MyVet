package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.CreatePlanCommand;
import com.vetos.modules.platformadmin.domain.AuditAction;
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
    private final RecordAuditLogUseCase recordAuditLogUseCase;

    @Transactional
    public UUID execute(CreatePlanCommand command, UUID platformAdminId, String platformAdminEmail) {
        if (planRepository.existsByCode(command.code())) {
            throw new PlanCodeAlreadyExistsConflictException(command.code());
        }
        Plan plan = Plan.create(command.code(), command.name(), command.monthlyPrice());
        UUID planId = planRepository.save(plan).getId();
        recordAuditLogUseCase.execute(
            platformAdminId, platformAdminEmail, AuditAction.PLAN_CREATED, "PLAN", planId,
            command.code() + " - " + command.name()
        );
        return planId;
    }
}
