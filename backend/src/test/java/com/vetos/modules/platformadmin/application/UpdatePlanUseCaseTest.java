package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.UpdatePlanCommand;
import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.exception.PlanNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdatePlanUseCaseTest {

    @Mock private PlanRepository planRepository;
    @Mock private RecordAuditLogUseCase recordAuditLogUseCase;

    @Test
    void should_updatePlanAndRecordAuditLog() {
        UUID planId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Plan plan = Plan.create("PRO", "Pro Plan", new BigDecimal("2000.00"));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        UpdatePlanUseCase useCase = new UpdatePlanUseCase(planRepository, recordAuditLogUseCase);

        useCase.execute(new UpdatePlanCommand(
            planId, "Pro Plan Yeni", new BigDecimal("2500.00"), null, null, null, null, List.of(), true
        ), adminId, "admin@vetly.com.tr");

        verify(planRepository).save(plan);
        verify(recordAuditLogUseCase).execute(eq(adminId), eq("admin@vetly.com.tr"), eq(AuditAction.PLAN_UPDATED), eq("PLAN"), eq(planId), any());
    }

    @Test
    void should_throwNotFound_when_planDoesNotExist() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.empty());
        UpdatePlanUseCase useCase = new UpdatePlanUseCase(planRepository, recordAuditLogUseCase);

        assertThatThrownBy(() -> useCase.execute(new UpdatePlanCommand(
            planId, "X", BigDecimal.ONE, null, null, null, null, List.of(), true
        ), UUID.randomUUID(), "admin@vetly.com.tr")).isInstanceOf(PlanNotFoundException.class);
        verifyNoInteractions(recordAuditLogUseCase);
    }
}
