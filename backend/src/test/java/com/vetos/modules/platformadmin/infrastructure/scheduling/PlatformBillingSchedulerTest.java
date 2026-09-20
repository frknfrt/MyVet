package com.vetos.modules.platformadmin.infrastructure.scheduling;

import com.vetos.modules.platformadmin.application.FlagOverdueAndSuspendUseCase;
import com.vetos.modules.platformadmin.application.GenerateDueInvoicesUseCase;
import com.vetos.modules.platformadmin.application.RemindDueSoonInvoicesUseCase;
import com.vetos.platform.concurrency.AdvisoryLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformBillingSchedulerTest {

    @Mock private GenerateDueInvoicesUseCase generateDueInvoicesUseCase;
    @Mock private RemindDueSoonInvoicesUseCase remindDueSoonInvoicesUseCase;
    @Mock private FlagOverdueAndSuspendUseCase flagOverdueAndSuspendUseCase;
    @Mock private AdvisoryLock advisoryLock;

    @Test
    void should_skipEntirely_when_lockNotAcquired() {
        PlatformBillingScheduler scheduler = new PlatformBillingScheduler(
            generateDueInvoicesUseCase, remindDueSoonInvoicesUseCase, flagOverdueAndSuspendUseCase, advisoryLock
        );
        when(advisoryLock.tryAcquire(anyLong())).thenReturn(false);

        scheduler.runDailyBilling();

        verifyNoInteractions(generateDueInvoicesUseCase, remindDueSoonInvoicesUseCase, flagOverdueAndSuspendUseCase);
    }

    @Test
    void should_runAllThreeSteps_when_lockAcquired() {
        PlatformBillingScheduler scheduler = new PlatformBillingScheduler(
            generateDueInvoicesUseCase, remindDueSoonInvoicesUseCase, flagOverdueAndSuspendUseCase, advisoryLock
        );
        when(advisoryLock.tryAcquire(anyLong())).thenReturn(true);

        scheduler.runDailyBilling();

        verify(generateDueInvoicesUseCase).execute(any());
        verify(remindDueSoonInvoicesUseCase).execute(any());
        verify(flagOverdueAndSuspendUseCase).execute(any());
    }
}
