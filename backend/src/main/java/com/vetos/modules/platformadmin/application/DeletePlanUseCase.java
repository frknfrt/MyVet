package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.exception.PlanNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeletePlanUseCase {

    private final PlanRepository planRepository;
    private final RecordAuditLogUseCase recordAuditLogUseCase;

    @Transactional
    public void execute(UUID planId, UUID platformAdminId, String platformAdminEmail) {
        planRepository.findById(planId).orElseThrow(() -> new PlanNotFoundException(planId));
        planRepository.deleteById(planId);
        recordAuditLogUseCase.execute(platformAdminId, platformAdminEmail, AuditAction.PLAN_DELETED, "PLAN", planId, null);
    }
}
