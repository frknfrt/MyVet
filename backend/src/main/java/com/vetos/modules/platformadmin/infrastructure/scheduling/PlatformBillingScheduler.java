package com.vetos.modules.platformadmin.infrastructure.scheduling;

import com.vetos.modules.platformadmin.application.FlagOverdueAndSuspendUseCase;
import com.vetos.modules.platformadmin.application.GenerateDueInvoicesUseCase;
import com.vetos.modules.platformadmin.application.RemindDueSoonInvoicesUseCase;
import com.vetos.platform.concurrency.AdvisoryLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Gunde bir kez calisir -- AppointmentReminderScheduler ile ayni desen.
 * Uc adim sirayla: fatura uret, son-gun hatirlat, gecikeni askiya al. Her
 * adim kendi try/catch'inde -- biri patlarsa digerleri yine de calisir.
 * Advisory lock, coklu instance'ta ayni gunun faturasinin iki kez
 * uretilmesini engeller (bkz. AppointmentReminderScheduler.REMINDER_LOCK_KEY
 * ile ayni desen, farkli bir sabit anahtarla).
 */
@Component
@Slf4j
@RequiredArgsConstructor
class PlatformBillingScheduler {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final long PLATFORM_BILLING_LOCK_KEY = 7_301_002; // REMINDER_LOCK_KEY'den farkli, ayri bir sabit

    private final GenerateDueInvoicesUseCase generateDueInvoicesUseCase;
    private final RemindDueSoonInvoicesUseCase remindDueSoonInvoicesUseCase;
    private final FlagOverdueAndSuspendUseCase flagOverdueAndSuspendUseCase;
    private final AdvisoryLock advisoryLock;

    @Scheduled(cron = "0 0 6 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void runDailyBilling() {
        if (!advisoryLock.tryAcquire(PLATFORM_BILLING_LOCK_KEY)) {
            log.info("Platform faturalama kilidi baska bir instance'da -- atlaniyor");
            return;
        }

        LocalDate today = LocalDate.now(ISTANBUL);

        try {
            generateDueInvoicesUseCase.execute(today);
        } catch (Exception e) {
            log.error("Fatura uretimi basarisiz: today={}", today, e);
        }
        try {
            remindDueSoonInvoicesUseCase.execute(today);
        } catch (Exception e) {
            log.error("Son-gun hatirlatmasi basarisiz: today={}", today, e);
        }
        try {
            flagOverdueAndSuspendUseCase.execute(today);
        } catch (Exception e) {
            log.error("Gecikme/askiya alma basarisiz: today={}", today, e);
        }
    }
}
