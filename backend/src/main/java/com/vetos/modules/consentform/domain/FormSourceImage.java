package com.vetos.modules.consentform.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "form_source_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FormSourceImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "storage_ref", nullable = false)
    private String storageRef;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public static FormSourceImage create(UUID tenantId, String storageRef, String contentType, UUID uploadedBy) {
        FormSourceImage image = new FormSourceImage();
        image.tenantId = tenantId;
        image.storageRef = storageRef;
        image.contentType = contentType;
        image.uploadedBy = uploadedBy;
        image.uploadedAt = Instant.now();
        return image;
    }
}
