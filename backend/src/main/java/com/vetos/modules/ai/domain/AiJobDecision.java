package com.vetos.modules.ai.domain;

import com.vetos.modules.ai.domain.exception.AiDecisionAlreadyRecordedException;
import com.vetos.modules.ai.domain.exception.AiDecisionNotYetMadeException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ai_job_decisions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AiJobDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID aiJobId;

    @Enumerated(EnumType.STRING)
    private DecisionStatus decisionStatus;

    @Column(columnDefinition = "text")
    private String appliedContent;

    private UUID decidedByStaffUserId;
    private Instant decidedAt;

    @Enumerated(EnumType.STRING)
    private AccuracyFeedback accuracyFeedback;
    private Instant feedbackAt;

    public static AiJobDecision createPending(UUID aiJobId) {
        AiJobDecision d = new AiJobDecision();
        d.aiJobId = aiJobId;
        return d;
    }

    public void decide(DecisionStatus status, String appliedContent, UUID decidedByStaffUserId) {
        Objects.requireNonNull(status, "status");
        if (this.decisionStatus != null) {
            throw new AiDecisionAlreadyRecordedException(this.aiJobId);
        }
        this.decisionStatus = status;
        this.appliedContent = appliedContent;
        this.decidedByStaffUserId = decidedByStaffUserId;
        this.decidedAt = Instant.now();
    }

    public void recordFeedback(AccuracyFeedback feedback) {
        Objects.requireNonNull(feedback, "feedback");
        if (this.decisionStatus == null) {
            throw new AiDecisionNotYetMadeException(this.aiJobId);
        }
        if (this.accuracyFeedback != null) {
            throw new AiDecisionAlreadyRecordedException(this.aiJobId);
        }
        this.accuracyFeedback = feedback;
        this.feedbackAt = Instant.now();
    }
}
