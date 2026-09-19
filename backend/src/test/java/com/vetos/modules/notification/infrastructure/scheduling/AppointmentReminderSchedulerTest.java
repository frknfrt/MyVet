package com.vetos.modules.notification.infrastructure.scheduling;

import com.vetos.modules.notification.application.SendAppointmentRemindersUseCase;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Koprulme kurali (tasarim dokumani S5): arka plan isi, @TenantId'li bir
 * entity'ye dokunmadan once TenantContext'i kurar, finally'de temizler.
 * Gercek Hibernate yok -- sadece TenantContext'in dogru deger ile
 * kuruldugu ve her tur sonunda temizlendigi dogrulanir.
 */
@ExtendWith(MockitoExtension.class)
class AppointmentReminderSchedulerTest {

    @Mock private TenantLookupPort tenantLookupPort;
    @Mock private SendAppointmentRemindersUseCase sendAppointmentRemindersUseCase;

    @Test
    void should_setTenantContext_forEachTenant_and_clearAfterwards() {
        AppointmentReminderScheduler scheduler =
            new AppointmentReminderScheduler(tenantLookupPort, sendAppointmentRemindersUseCase);
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        List<UUID> seenInsideUseCase = new ArrayList<>();
        when(tenantLookupPort.findActiveTenantIds()).thenReturn(List.of(tenantA, tenantB));
        when(sendAppointmentRemindersUseCase.execute(any(), any(Instant.class), any(Instant.class)))
            .thenAnswer(invocation -> {
                seenInsideUseCase.add(TenantContext.current());
                return 0;
            });

        scheduler.sendTomorrowReminders();

        assertThat(seenInsideUseCase).containsExactly(tenantA, tenantB);
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void should_clearTenantContext_evenWhenUseCaseThrows() {
        AppointmentReminderScheduler scheduler =
            new AppointmentReminderScheduler(tenantLookupPort, sendAppointmentRemindersUseCase);
        UUID tenantA = UUID.randomUUID();
        when(tenantLookupPort.findActiveTenantIds()).thenReturn(List.of(tenantA));
        when(sendAppointmentRemindersUseCase.execute(eq(tenantA), any(Instant.class), any(Instant.class)))
            .thenThrow(new RuntimeException("gonderim patladi"));

        scheduler.sendTomorrowReminders();

        // Mevcut try/catch hatayi yutar; koprulme yine de temizlemis olmali --
        // aksi halde ThreadLocal, scheduler thread'inde SIZAR ve bir sonraki
        // is yanlis kiracinin verisini gorur.
        assertThat(TenantContext.currentOrNull()).isNull();
    }
}
