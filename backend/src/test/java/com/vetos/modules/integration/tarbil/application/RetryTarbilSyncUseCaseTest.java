package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncType;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSyncLogNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryTarbilSyncUseCaseTest {

    @Mock private TarbilSyncLogRepository tarbilSyncLogRepository;
    @Mock private TarbilSyncExecutor tarbilSyncExecutor;

    @Test
    void should_throwNotFound_when_logBelongsToAnotherTenant() {
        RetryTarbilSyncUseCase useCase = new RetryTarbilSyncUseCase(tarbilSyncLogRepository, tarbilSyncExecutor);
        UUID callerTenantId = UUID.randomUUID();
        UUID foreignTenantId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        TarbilSyncLog log = TarbilSyncLog.queue(foreignTenantId, UUID.randomUUID(), TarbilSyncType.VACCINATION, "{}");
        when(tarbilSyncLogRepository.findById(logId)).thenReturn(Optional.of(log));

        assertThatThrownBy(() -> useCase.execute(callerTenantId, logId))
            .isInstanceOf(TarbilSyncLogNotFoundException.class);

        verify(tarbilSyncLogRepository, never()).save(any());
        verify(tarbilSyncExecutor, never()).attemptSync(any());
    }

    @Test
    void should_retry_when_logBelongsToCallerTenant() {
        RetryTarbilSyncUseCase useCase = new RetryTarbilSyncUseCase(tarbilSyncLogRepository, tarbilSyncExecutor);
        UUID tenantId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        TarbilSyncLog log = TarbilSyncLog.queue(tenantId, UUID.randomUUID(), TarbilSyncType.VACCINATION, "{}");
        when(tarbilSyncLogRepository.findById(logId)).thenReturn(Optional.of(log));

        useCase.execute(tenantId, logId);

        verify(tarbilSyncLogRepository).save(log);
        verify(tarbilSyncExecutor).attemptSync(logId);
    }
}
