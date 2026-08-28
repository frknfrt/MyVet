package com.vetos.modules.platformadmin.infrastructure.scheduling;

import com.vetos.modules.platformadmin.application.FlagOverdueAndSuspendUseCase;
import com.vetos.modules.platformadmin.application.GenerateDueInvoicesUseCase;
import com.vetos.modules.platformadmin.application.RemindDueSoonInvoicesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Gunde bir kez calisir -- AppointmentReminderScheduler ile ayni desen.
 * Uc adim sirayla: fatura uret, son-gun hatirlat, gecikeni askiya al. Her
 * adim kendi try/catch'inde -- biri patlarsa digerleri yine de calisir.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class PlatformBillingScheduler {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final GenerateDueInvoicesUseCase generateDueInvoicesUseCase;
    private final RemindDueSoonInvoicesUseCase remindDueSoonInvoicesUseCase;
    private final FlagOverdueAndSuspendUseCase flagOverdueAndSuspendUseCase;

    @Scheduled(cron = "0 0 6 * * *", zone = "Europe/Istanbul")
    public void runDailyBilling() {
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
