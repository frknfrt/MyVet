package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.TrialEmailPort;
import com.vetos.modules.platformadmin.domain.TrialSmsPort;
import com.vetos.modules.tenant.domain.EndingTrial;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Deneme bitis hatirlatmalari (spec 2026-11-13 S4.5): bitise 2 gun kala ve son gun, klinik
 * yoneticisine SMS + e-posta. PlatformBillingScheduler gunde bir kez (advisory lock altinda)
 * cagirdigi icin tarih esitligi tekrar gonderimi kendiliginden onler. Bir klinikteki hata
 * digerlerini durdurmaz.
 */
@Service
@Slf4j
public class RemindEndingTrialsUseCase {

    private static final int DAYS_BEFORE = 2;

    private final TenantAdminPort tenantAdminPort;
    private final TrialSmsPort trialSmsPort;
    private final TrialEmailPort trialEmailPort;
    private final String upgradeUrl;

    public RemindEndingTrialsUseCase(
        TenantAdminPort tenantAdminPort, TrialSmsPort trialSmsPort, TrialEmailPort trialEmailPort,
        @Value("${app.frontend-base-url}") String frontendBaseUrl
    ) {
        this.tenantAdminPort = tenantAdminPort;
        this.trialSmsPort = trialSmsPort;
        this.trialEmailPort = trialEmailPort;
        this.upgradeUrl = frontendBaseUrl + "/ayarlar/abonelik";
    }

    /** @return hatirlatma gonderilen klinik sayisi */
    public int execute(LocalDate today) {
        int notified = 0;
        for (EndingTrial trial : tenantAdminPort.listTrialsEndingOn(today.plusDays(DAYS_BEFORE))) {
            notified += notify(trial, false) ? 1 : 0;
        }
        for (EndingTrial trial : tenantAdminPort.listTrialsEndingOn(today)) {
            notified += notify(trial, true) ? 1 : 0;
        }
        return notified;
    }

    private boolean notify(EndingTrial trial, boolean lastDay) {
        try {
            String clinicName = tenantAdminPort.getOverview(trial.tenantId()).name();
            Optional<String> phone = tenantAdminPort.findBillingContactPhone(trial.tenantId());
            Optional<String> email = tenantAdminPort.findBillingContactEmail(trial.tenantId());
            if (lastDay) {
                phone.ifPresent(p -> trialSmsPort.sendTrialLastDay(p, clinicName));
                email.ifPresent(e -> trialEmailPort.sendTrialLastDay(e, clinicName, upgradeUrl));
            } else {
                phone.ifPresent(p -> trialSmsPort.sendTrialEndingSoon(p, clinicName, trial.endsAt()));
                email.ifPresent(e -> trialEmailPort.sendTrialEndingSoon(e, clinicName, trial.endsAt(), upgradeUrl));
            }
            return true;
        } catch (RuntimeException e) {
            log.warn("Deneme hatirlatmasi gonderilemedi: tenantId={}, hata={}", trial.tenantId(), e.getMessage());
            return false;
        }
    }
}
