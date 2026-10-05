package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.AuditLogEntry;
import com.vetos.modules.platformadmin.domain.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAuditLogUseCaseTest {

    @Mock private AuditLogRepository auditLogRepository;

    @Test
    void should_returnEntries_inRepositoryOrder() {
        AuditLogEntry e1 = AuditLogEntry.record(UUID.randomUUID(), "a@vetly.com.tr", AuditAction.TENANT_SUSPENDED, "TENANT", UUID.randomUUID(), null);
        AuditLogEntry e2 = AuditLogEntry.record(UUID.randomUUID(), "b@vetly.com.tr", AuditAction.PLAN_CREATED, "PLAN", UUID.randomUUID(), null);
        when(auditLogRepository.findTop200ByOrderByCreatedAtDesc()).thenReturn(List.of(e2, e1));
        ListAuditLogUseCase useCase = new ListAuditLogUseCase(auditLogRepository);

        List<AuditLogEntry> result = useCase.execute();

        assertThat(result).containsExactly(e2, e1);
    }
}
