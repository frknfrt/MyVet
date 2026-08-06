package com.vetos.modules.notification.infrastructure.scheduling;

import com.vetos.modules.notification.application.SendAppointmentRemindersUseCase;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    private final TenantLookupPort tenantLookupPort;
    private final SendAppointmentRemindersUseCase sendAppointmentRemindersUseCase;

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Istanbul")
    public void sendTomorrowReminders() {
        Instant tomorrowStart = LocalDate.now(ISTANBUL).plusDays(1).atStartOfDay(ISTANBUL).toInstant();
        Instant tomorrowEnd = tomorrowStart.plus(1, ChronoUnit.DAYS);

        for (UUID tenantId : tenantLookupPort.findActiveTenantIds()) {
            try {
                int queued = sendAppointmentRemindersUseCase.execute(tenantId, tomorrowStart, tomorrowEnd);
                if (queued > 0) {
                    log.info("Randevu hatirlatmasi kuyruklandi: tenantId={}, adet={}", tenantId, queued);
                }
            } catch (Exception e) {
                log.error("Randevu hatirlatma isi basarisiz: tenantId={}", tenantId, e);
            }
        }
    }
}
