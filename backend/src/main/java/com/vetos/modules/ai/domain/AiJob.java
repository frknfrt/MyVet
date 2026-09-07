package com.vetos.modules.ai.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_jobs")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AiJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiTaskType taskType;

    @Column(nullable = false)
    private UUID encounterId;

    @Column(nullable = false, columnDefinition = "text")
    private String suggestionText;

    @Column(nullable = false)
    private String modelName;

    @Column(nullable = false)
    private String modelVersion;

    @Column(nullable = false)
    private UUID requestedByStaffUserId;

    @Column(nullable = false)
    private Instant createdAt;

    public static AiJob create(
        UUID tenantId, AiTaskType taskType, UUID encounterId,
        String suggestionText, String modelName, String modelVersion, UUID requestedByStaffUserId
    ) {
        AiJob job = new AiJob();
        job.tenantId = tenantId;
        job.taskType = taskType;
        job.encounterId = encounterId;
        job.suggestionText = suggestionText;
        job.modelName = modelName;
        job.modelVersion = modelVersion;
        job.requestedByStaffUserId = requestedByStaffUserId;
        job.createdAt = Instant.now();
        return job;
    }
}
