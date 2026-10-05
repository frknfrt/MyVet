package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.CreatePlanCommand;
import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.exception.PlanCodeAlreadyExistsConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatePlanUseCaseTest {

    @Mock private PlanRepository planRepository;
    @Mock private RecordAuditLogUseCase recordAuditLogUseCase;

    @Test
    void should_createPlanAndRecordAuditLog() {
        UUID adminId = UUID.randomUUID();
        when(planRepository.existsByCode("PRO")).thenReturn(false);
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            Plan plan = inv.getArgument(0);
            ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
            return plan;
        });
        CreatePlanUseCase useCase = new CreatePlanUseCase(planRepository, recordAuditLogUseCase);

        UUID result = useCase.execute(new CreatePlanCommand("PRO", "Pro Plan", new BigDecimal("2000.00")), adminId, "admin@vetly.com.tr");

        assertThat(result).isNotNull();
        verify(recordAuditLogUseCase).execute(eq(adminId), eq("admin@vetly.com.tr"), eq(AuditAction.PLAN_CREATED), eq("PLAN"), eq(result), any());
    }

    @Test
    void should_throwConflict_when_codeAlreadyExists() {
        when(planRepository.existsByCode("PRO")).thenReturn(true);
        CreatePlanUseCase useCase = new CreatePlanUseCase(planRepository, recordAuditLogUseCase);

        assertThatThrownBy(() -> useCase.execute(new CreatePlanCommand("PRO", "Pro Plan", new BigDecimal("2000.00")), UUID.randomUUID(), "admin@vetly.com.tr"))
            .isInstanceOf(PlanCodeAlreadyExistsConflictException.class);
        verifyNoInteractions(recordAuditLogUseCase);
    }
}
