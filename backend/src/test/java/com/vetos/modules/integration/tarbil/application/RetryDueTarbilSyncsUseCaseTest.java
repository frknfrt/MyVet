package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryDueTarbilSyncsUseCaseTest {

    @Mock private TarbilSyncLogRepository tarbilSyncLogRepository;
    @Mock private TarbilSyncExecutor tarbilSyncExecutor;

    private TarbilSyncLog aFailedLog() {
        TarbilSyncLog log = TarbilSyncLog.queue(UUID.randomUUID(), UUID.randomUUID(), TarbilSyncType.VACCINATION, "{}");
        log.markFailed(Instant.now());
        return log;
    }

    @Test
    void should_markClaimedLogsRetrying_and_returnClaimedCount() {
        RetryDueTarbilSyncsUseCase useCase = new RetryDueTarbilSyncsUseCase(tarbilSyncLogRepository, tarbilSyncExecutor);
        TarbilSyncLog log1 = aFailedLog();
        when(tarbilSyncLogRepository.claimDueForRetry(any(), anyInt())).thenReturn(List.of(log1));

        int claimedCount = useCase.execute();

        assertThat(claimedCount).isEqualTo(1);
        assertThat(log1.getStatus()).isEqualTo(TarbilSyncStatus.PENDING);
        verify(tarbilSyncLogRepository).save(log1);
    }

    @Test
    void should_returnZero_when_nothingDue() {
        RetryDueTarbilSyncsUseCase useCase = new RetryDueTarbilSyncsUseCase(tarbilSyncLogRepository, tarbilSyncExecutor);
        when(tarbilSyncLogRepository.claimDueForRetry(any(), anyInt())).thenReturn(List.of());

        int claimedCount = useCase.execute();

        assertThat(claimedCount).isZero();
        verify(tarbilSyncLogRepository, never()).save(any());
    }
}
