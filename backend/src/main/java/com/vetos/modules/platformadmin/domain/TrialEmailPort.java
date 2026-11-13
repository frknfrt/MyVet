package com.vetos.modules.platformadmin.domain;

import java.time.LocalDate;

/** Deneme e-postalari. SMTP tanimli degilse adaptor simule eder; gonderim hatasi exception firlatmaz. */
public interface TrialEmailPort {
    void sendWelcome(String email, String fullName, String clinicName, LocalDate trialEndsAt, String loginUrl);
    void sendTrialEndingSoon(String email, String clinicName, LocalDate trialEndsAt, String upgradeUrl);
    void sendTrialLastDay(String email, String clinicName, String upgradeUrl);
}
