package com.vetos.modules.platformadmin.application;

public record StartTrialSignupCommand(
    String fullName, String phone, String email, String clinicName, String city,
    String chamberRegistrationNo, String password, boolean consentAccepted, String clientIp
) {}
