package com.vetos.modules.platformadmin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Vitrin sitesinden self-servis deneme kaydi (spec 2026-11-13 S4.1). Kiraciya ait degildir:
 * klinik, telefon SMS koduyla dogrulanana kadar olusmaz. Sifre ve kod yalnizca hash olarak
 * tutulur ve kayit tamamlaninca silinir.
 */
@Entity
@Table(name = "trial_signup_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrialSignupRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(name = "clinic_name", nullable = false)
    private String clinicName;

    @Column(nullable = false)
    private String city;

    @Column(name = "chamber_registration_no", nullable = false)
    private String chamberRegistrationNo;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "otp_hash")
    private String otpHash;

    @Column(name = "otp_expires_at")
    private Instant otpExpiresAt;

    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts;

    @Column(name = "otp_send_count", nullable = false)
    private int otpSendCount;

    @Column(name = "last_otp_sent_at")
    private Instant lastOtpSentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrialSignupRequestStatus status;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "consent_accepted_at", nullable = false)
    private Instant consentAcceptedAt;

    @Column(name = "consent_ip")
    private String consentIp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static TrialSignupRequest create(
        String fullName, String email, String phoneE164, String clinicName, String city,
        String chamberRegistrationNo, String passwordHash, Instant now, String consentIp
    ) {
        TrialSignupRequest request = new TrialSignupRequest();
        request.fullName = fullName;
        request.email = email;
        request.phone = phoneE164;
        request.clinicName = clinicName;
        request.city = city;
        request.chamberRegistrationNo = chamberRegistrationNo;
        request.passwordHash = passwordHash;
        request.status = TrialSignupRequestStatus.PENDING;
        request.consentAcceptedAt = now;
        request.consentIp = consentIp;
        request.createdAt = now;
        return request;
    }

    /**
     * Yeni kod. Yanlis deneme sayaci SIFIRLANMAZ (spec 4.1): istek basina toplam deneme hakki
     * sinirlidir, hak bitince kullanici formu bastan doldurur.
     */
    public void issueCode(String otpHash, Instant now, int ttlSeconds) {
        this.otpHash = otpHash;
        this.otpExpiresAt = now.plusSeconds(ttlSeconds);
        this.otpSendCount++;
        this.lastOtpSentAt = now;
    }

    public boolean canResend(Instant now, int cooldownSeconds, int maxSends) {
        if (otpSendCount >= maxSends) {
            return false;
        }
        return lastOtpSentAt == null || !now.isBefore(lastOtpSentAt.plusSeconds(cooldownSeconds));
    }

    public int secondsUntilResend(Instant now, int cooldownSeconds) {
        if (lastOtpSentAt == null) {
            return 0;
        }
        long remaining = lastOtpSentAt.plusSeconds(cooldownSeconds).getEpochSecond() - now.getEpochSecond();
        return (int) Math.max(0, remaining);
    }

    public void registerFailedAttempt() {
        this.otpAttempts++;
    }

    public boolean hasAttemptsLeft(int maxAttempts) {
        return otpAttempts < maxAttempts;
    }

    public int attemptsLeft(int maxAttempts) {
        return Math.max(0, maxAttempts - otpAttempts);
    }

    public boolean isCodeExpired(Instant now) {
        return otpExpiresAt == null || now.isAfter(otpExpiresAt);
    }

    public boolean isCompleted() {
        return status == TrialSignupRequestStatus.COMPLETED;
    }

    /** Klinik olusturuldu: gizli hash alanlari artik gereksiz, silinir. */
    public void complete(UUID tenantId, Instant now) {
        this.status = TrialSignupRequestStatus.COMPLETED;
        this.tenantId = tenantId;
        this.completedAt = now;
        this.otpHash = null;
        this.passwordHash = null;
    }
}
