package com.vetos.modules.consentform.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "form_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FormTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "title", nullable = false)
    private String title;

    /** FormTemplateContentCodec ciktisi (TEXT). */
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "source_image_id")
    private UUID sourceImageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FormTemplateStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static FormTemplate create(UUID tenantId, String title, String content, UUID sourceImageId, UUID staffId) {
        FormTemplate t = new FormTemplate();
        Instant now = Instant.now();
        t.tenantId = tenantId;
        t.title = title;
        t.content = content;
        t.sourceImageId = sourceImageId;
        t.status = FormTemplateStatus.ACTIVE;
        t.createdBy = staffId;
        t.createdAt = now;
        t.updatedBy = staffId;
        t.updatedAt = now;
        return t;
    }

    public void update(String title, String content, UUID staffId) {
        this.title = title;
        this.content = content;
        this.updatedBy = staffId;
        this.updatedAt = Instant.now();
    }

    public void archive(UUID staffId) {
        this.status = FormTemplateStatus.ARCHIVED;
        this.updatedBy = staffId;
        this.updatedAt = Instant.now();
    }
}
