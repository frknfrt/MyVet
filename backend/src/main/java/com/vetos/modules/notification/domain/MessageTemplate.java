package com.vetos.modules.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateChannel channel;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static MessageTemplate create(UUID tenantId, String name, TemplateChannel channel, String category, String body) {
        MessageTemplate template = new MessageTemplate();
        template.tenantId = tenantId;
        template.name = name;
        template.channel = channel;
        template.category = category;
        template.body = body;
        template.createdAt = Instant.now();
        template.updatedAt = Instant.now();
        return template;
    }

    public void update(String name, TemplateChannel channel, String category, String body) {
        this.name = name;
        this.channel = channel;
        this.category = category;
        this.body = body;
        this.updatedAt = Instant.now();
    }
}
