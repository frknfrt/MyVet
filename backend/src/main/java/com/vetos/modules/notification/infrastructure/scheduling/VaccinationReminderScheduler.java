package com.vetos.modules.notification.infrastructure.scheduling;

import com.vetos.modules.notification.application.SendVaccinationRemindersUseCase;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import com.vetos.platform.concurrency.AdvisoryLock;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Her gun 09:30'da (Europe/Istanbul) calisir, yarin tarihi asi zamani gelen
 * (nextDueDate) hastalar icin sahibe hatirlatma kuyruklar -- bkz.
 * SendVaccinationRemindersUseCase. AppointmentReminderScheduler ile ayni
 * desen, ayni saatte tek islemde yigilmasin diye 30 dk sonraya alindi.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class VaccinationReminderScheduler {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final long REMINDER_LOCK_KEY = 7_301_002; // AppointmentReminderScheduler'in kilidinden (7_301_001) farkli, sabit

    private final TenantLookupPort tenantLookupPort;
    private final SendVaccinationRemindersUseCase sendVaccinationRemindersUseCase;
    private final AdvisoryLock advisoryLock;

    @Scheduled(cron = "0 30 9 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void sendTomorrowReminders() {
        if (!advisoryLock.tryAcquire(REMINDER_LOCK_KEY)) {
            log.info("Asi hatirlatma kilidi baska bir instance'da -- atlaniyor");
            return;
        }

        LocalDate tomorrow = LocalDate.now(ISTANBUL).plusDays(1);

        for (UUID tenantId : tenantLookupPort.findActiveTenantIds()) {
            TenantContext.set(tenantId);
            try {
                int queued = sendVaccinationRemindersUseCase.execute(tenantId, tomorrow);
                if (queued > 0) {
                    log.info("Asi hatirlatmasi kuyruklandi: tenantId={}, adet={}", tenantId, queued);
                }
            } catch (Exception e) {
                log.error("Asi hatirlatma isi basarisiz: tenantId={}", tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
