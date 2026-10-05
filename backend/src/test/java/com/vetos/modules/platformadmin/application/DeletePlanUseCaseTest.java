package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.exception.PlanNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeletePlanUseCaseTest {

    @Mock private PlanRepository planRepository;
    @Mock private RecordAuditLogUseCase recordAuditLogUseCase;

    @Test
    void should_deletePlanAndRecordAuditLog() {
        UUID planId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.of(Plan.create("PRO", "Pro Plan", new BigDecimal("2000.00"))));
        DeletePlanUseCase useCase = new DeletePlanUseCase(planRepository, recordAuditLogUseCase);

        useCase.execute(planId, adminId, "admin@vetly.com.tr");

        verify(planRepository).deleteById(planId);
        verify(recordAuditLogUseCase).execute(eq(adminId), eq("admin@vetly.com.tr"), eq(AuditAction.PLAN_DELETED), eq("PLAN"), eq(planId), any());
    }

    @Test
    void should_throwNotFound_when_planDoesNotExist() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.empty());
        DeletePlanUseCase useCase = new DeletePlanUseCase(planRepository, recordAuditLogUseCase);

        assertThatThrownBy(() -> useCase.execute(planId, UUID.randomUUID(), "admin@vetly.com.tr")).isInstanceOf(PlanNotFoundException.class);
        verifyNoInteractions(recordAuditLogUseCase);
    }
}
