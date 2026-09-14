package com.vetos.modules.patient.domain;

import com.vetos.modules.patient.domain.exception.InvalidNationalIdException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "owners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Owner {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(nullable = false)
    private String phone;

    @Column(name = "secondary_phone")
    private String secondaryPhone;

    private String email;

    @Column(name = "national_id")
    private String nationalId;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    private String address;

    private String city;

    private String district;

    private String occupation;

    @Column(name = "referral_source")
    private String referralSource;

    @Column(name = "client_discount", nullable = false)
    private BigDecimal clientDiscount;

    @Column(name = "critical_alert")
    private String criticalAlert;

    private String notes;

    @Column(name = "marketing_consent", nullable = false)
    private boolean marketingConsent;

    @Column(name = "sms_consent", nullable = false)
    private boolean smsConsent;

    @Column(name = "whatsapp_consent", nullable = false)
    private boolean whatsappConsent;

    @Column(name = "notification_consent", nullable = false)
    private boolean notificationConsent;

    @Column(name = "protocol_number")
    private String protocolNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Owner register(UUID tenantId, String fullName, String phone, String email, String address) {
        Owner owner = new Owner();
        owner.tenantId = tenantId;
        owner.fullName = fullName;
        owner.phone = phone;
        owner.email = email;
        owner.address = address;
        owner.marketingConsent = false;
        owner.clientDiscount = BigDecimal.ZERO;
        owner.smsConsent = true;
        owner.whatsappConsent = true;
        owner.notificationConsent = true;
        owner.createdAt = Instant.now();
        return owner;
    }

    public void updateContactInfo(String phone, String email, String address) {
        this.phone = phone;
        this.email = email;
        this.address = address;
    }

    // fullName kayit sirasinda bir kere set edilip hicbir yerden
    // degistirilemiyordu -- personel yanlis/eksik girdiginde (orn. sadece
    // ad, soyadsiz) duzeltecek bir yol yoktu. e-Fatura'nin GIB soyad
    // kuralini karsilayabilmek icin duzenlenebilir hale getirildi.
    public void updateFullName(String fullName) {
        this.fullName = fullName;
    }

    public void updateDetails(
        String middleName, String secondaryPhone, String city, String district, String occupation,
        String referralSource, BigDecimal clientDiscount, String criticalAlert, String notes,
        boolean smsConsent, boolean whatsappConsent, boolean notificationConsent, String protocolNumber,
        LocalDate birthDate, String nationalId
    ) {
        this.middleName = middleName;
        this.secondaryPhone = secondaryPhone;
        this.city = city;
        this.district = district;
        this.occupation = occupation;
        this.referralSource = referralSource;
        this.clientDiscount = clientDiscount != null ? clientDiscount : BigDecimal.ZERO;
        this.criticalAlert = criticalAlert;
        this.notes = notes;
        this.smsConsent = smsConsent;
        this.whatsappConsent = whatsappConsent;
        this.notificationConsent = notificationConsent;
        this.protocolNumber = protocolNumber;
        this.birthDate = birthDate;
        updateNationalId(nationalId);
    }

    public void setMarketingConsent(boolean marketingConsent) {
        this.marketingConsent = marketingConsent;
    }

    // TC kimlik alani opsiyonel -- ama doluysa resmi checksum algoritmasini
    // gecmek zorunda (bkz. TcKimlikValidator, KVKK karari icin
    // docs/implementation-plan.md "TCKN karari" notu).
    public void updateNationalId(String nationalId) {
        if (nationalId != null && !nationalId.isBlank() && !TcKimlikValidator.isValid(nationalId)) {
            throw new InvalidNationalIdException();
        }
        this.nationalId = (nationalId == null || nationalId.isBlank()) ? null : nationalId;
    }
}
