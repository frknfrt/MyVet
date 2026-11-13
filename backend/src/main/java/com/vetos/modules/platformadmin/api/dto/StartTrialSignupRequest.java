package com.vetos.modules.platformadmin.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartTrialSignupRequest(
    @NotBlank @Size(max = 120) String fullName,
    @NotBlank @Size(max = 30) String phone,
    @NotBlank @Email @Size(max = 200) String email,
    @NotBlank @Size(max = 160) String clinicName,
    @NotBlank @Size(max = 60) String city,
    @NotBlank @Size(max = 30) String chamberRegistrationNo,
    @NotBlank @Size(min = 8, max = 100) String password,
    @AssertTrue(message = "Kullanım Şartları ve KVKK Aydınlatma Metni kabul edilmelidir") boolean consentAccepted
) {}
