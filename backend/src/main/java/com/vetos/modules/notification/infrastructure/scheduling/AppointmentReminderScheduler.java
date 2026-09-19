package com.vetos.modules.notification.infrastructure.scheduling;

import com.vetos.modules.notification.application.SendAppointmentRemindersUseCase;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import com.vetos.platform.concurrency.AdvisoryLock;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Her gun 09:00'da (Europe/Istanbul) calisir, yarin CONFIRMED durumda olan
 * randevular icin hatirlatma kuyruklar. Gercek bir mesaj kuyrugu/cron
 * orkestratoru (RabbitMQ, Kubernetes CronJob) Faz 1'de kurulu degil --
 * Spring @Scheduled ile tek instance'lik MVP dagitimina yeterli.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class AppointmentReminderScheduler {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final long REMINDER_LOCK_KEY = 7_301_001; // sabit, bu is icin ayrilmis rastgele bir anahtar

    private final TenantLookupPort tenantLookupPort;
    private final SendAppointmentRemindersUseCase sendAppointmentRemindersUseCase;
    private final AdvisoryLock advisoryLock;

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void sendTomorrowReminders() {
        if (!advisoryLock.tryAcquire(REMINDER_LOCK_KEY)) {
            log.info("Randevu hatirlatma kilidi baska bir instance'da -- atlaniyor");
            return;
        }

        Instant tomorrowStart = LocalDate.now(ISTANBUL).plusDays(1).atStartOfDay(ISTANBUL).toInstant();
        Instant tomorrowEnd = tomorrowStart.plus(1, ChronoUnit.DAYS);

        for (UUID tenantId : tenantLookupPort.findActiveTenantIds()) {
            // Koprulme kurali: bu is bir HTTP istegi icinde calismiyor, ama
            // tenantId elimizde. @TenantId'li entity'lere (Patient, Encounter...)
            // dokunmadan once TenantContext kurulmali -- aksi halde sorgu root
            // Session'da, yani FILTRESIZ calisir.
            TenantContext.set(tenantId);
            try {
                int queued = sendAppointmentRemindersUseCase.execute(tenantId, tomorrowStart, tomorrowEnd);
                if (queued > 0) {
                    log.info("Randevu hatirlatmasi kuyruklandi: tenantId={}, adet={}", tenantId, queued);
                }
            } catch (Exception e) {
                log.error("Randevu hatirlatma isi basarisiz: tenantId={}", tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
