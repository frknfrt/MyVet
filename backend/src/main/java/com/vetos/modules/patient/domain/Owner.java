package com.vetos.modules.patient.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
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

    @Column(nullable = false)
    private String phone;

    private String email;

    @Column(name = "national_id_masked")
    private String nationalIdMasked;

    private String address;

    @Column(name = "marketing_consent", nullable = false)
    private boolean marketingConsent;

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
        owner.createdAt = Instant.now();
        return owner;
    }

    public void updateContactInfo(String phone, String email, String address) {
        this.phone = phone;
        this.email = email;
        this.address = address;
    }

    public void setMarketingConsent(boolean marketingConsent) {
        this.marketingConsent = marketingConsent;
    }

    public void updateNationalIdMasked(String nationalIdMasked) {
        this.nationalIdMasked = nationalIdMasked;
    }
}
