package com.vetos.modules.platformadmin.domain;

import java.time.LocalDate;

/**
 * Deneme kaydi SMS bildirimleri. PlatformBillingSmsPort ile ayni gerekce: kiraci-ici
 * NotificationSendPort TenantContext ister, burada henuz kiraci yok. Kimlik bilgisi
 * tanimli degilse adaptor simule eder.
 */
public interface TrialSmsPort {
    void sendVerificationCode(String phoneE164, String code);
    void sendWelcome(String phoneE164, String clinicName, LocalDate trialEndsAt, String loginUrl);
    void sendTrialEndingSoon(String phoneE164, String clinicName, LocalDate trialEndsAt);
    void sendTrialLastDay(String phoneE164, String clinicName);
}
