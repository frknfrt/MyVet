package com.vetos.modules.consentform.domain;

import com.vetos.modules.consentform.domain.exception.SignedFormAlreadyVoidedException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Imzali form: olusturulduktan sonra degismez; yalniz voidForm ile iptal edilir. */
@Entity
@Table(name = "signed_forms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SignedForm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "template_id", nullable = false, updatable = false)
    private UUID templateId;

    @Column(name = "template_title", nullable = false, updatable = false)
    private String templateTitle;

    @Column(name = "template_snapshot", nullable = false, updatable = false)
    private String templateSnapshot;

    @Column(name = "values_json", nullable = false, updatable = false)
    private String valuesJson;

    @Column(name = "signature_storage_ref", nullable = false, updatable = false)
    private String signatureStorageRef;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "encounter_id", updatable = false)
    private UUID encounterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signer_type", nullable = false, updatable = false)
    private SignerType signerType;

    @Column(name = "signer_name", nullable = false, updatable = false)
    private String signerName;

    @Column(name = "signer_national_id", updatable = false)
    private String signerNationalId;

    @Column(name = "signer_phone", updatable = false)
    private String signerPhone;

    @Column(name = "signed_by", nullable = false, updatable = false)
    private UUID signedBy;

    @Column(name = "signed_at", nullable = false, updatable = false)
    private Instant signedAt;

    @Column(name = "client_ip", updatable = false)
    private String clientIp;

    @Column(name = "user_agent", updatable = false)
    private String userAgent;

    @Column(name = "content_digest", nullable = false, updatable = false)
    private String contentDigest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SignedFormStatus status;

    @Column(name = "void_reason")
    private String voidReason;

    @Column(name = "voided_by")
    private UUID voidedBy;

    @Column(name = "voided_at")
    private Instant voidedAt;

    public static SignedForm create(
        UUID tenantId, UUID templateId, String templateTitle, String templateSnapshot, String valuesJson,
        String signatureStorageRef, UUID patientId, UUID ownerId, UUID encounterId,
        SignerType signerType, String signerName, String signerNationalId, String signerPhone,
        UUID signedBy, Instant signedAt, String clientIp, String userAgent, String contentDigest
    ) {
        SignedForm f = new SignedForm();
        f.tenantId = tenantId;
        f.templateId = templateId;
        f.templateTitle = templateTitle;
        f.templateSnapshot = templateSnapshot;
        f.valuesJson = valuesJson;
        f.signatureStorageRef = signatureStorageRef;
        f.patientId = patientId;
        f.ownerId = ownerId;
        f.encounterId = encounterId;
        f.signerType = signerType;
        f.signerName = signerName;
        f.signerNationalId = signerNationalId;
        f.signerPhone = signerPhone;
        f.signedBy = signedBy;
        f.signedAt = signedAt;
        f.clientIp = clientIp;
        f.userAgent = userAgent;
        f.contentDigest = contentDigest;
        f.status = SignedFormStatus.VALID;
        return f;
    }

    public void voidForm(String reason, UUID staffId) {
        if (status == SignedFormStatus.VOIDED) {
            throw new SignedFormAlreadyVoidedException();
        }
        this.status = SignedFormStatus.VOIDED;
        this.voidReason = reason;
        this.voidedBy = staffId;
        this.voidedAt = Instant.now();
    }
}
