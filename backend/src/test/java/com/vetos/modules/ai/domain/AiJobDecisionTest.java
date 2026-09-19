package com.vetos.modules.ai.domain;

import com.vetos.modules.ai.domain.exception.AiDecisionAlreadyRecordedConflictException;
import com.vetos.modules.ai.domain.exception.AiDecisionMissingAppliedContentException;
import com.vetos.modules.ai.domain.exception.AiDecisionNotYetMadeException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiJobDecisionTest {

    @Test
    void should_recordDecision_when_decidedFirstTime() {
        UUID staffUserId = UUID.randomUUID();
        AiJobDecision decision = AiJobDecision.createPending(UUID.randomUUID(), UUID.randomUUID());

        decision.decide(DecisionStatus.ACCEPTED_AS_IS, null, staffUserId);

        assertThat(decision.getDecisionStatus()).isEqualTo(DecisionStatus.ACCEPTED_AS_IS);
        assertThat(decision.getAppliedContent()).isNull();
        assertThat(decision.getDecidedByStaffUserId()).isEqualTo(staffUserId);
        assertThat(decision.getDecidedAt()).isNotNull();
    }

    @Test
    void should_storeAppliedContent_when_acceptedWithEdits() {
        AiJobDecision decision = AiJobDecision.createPending(UUID.randomUUID(), UUID.randomUUID());

        decision.decide(DecisionStatus.ACCEPTED_WITH_EDITS, "Duzenlenmis tedavi plani", UUID.randomUUID());

        assertThat(decision.getDecisionStatus()).isEqualTo(DecisionStatus.ACCEPTED_WITH_EDITS);
        assertThat(decision.getAppliedContent()).isEqualTo("Duzenlenmis tedavi plani");
    }

    @Test
    void should_throwAlreadyRecorded_when_decidingTwice() {
        AiJobDecision decision = AiJobDecision.createPending(UUID.randomUUID(), UUID.randomUUID());
        decision.decide(DecisionStatus.REJECTED, null, UUID.randomUUID());

        assertThatThrownBy(() -> decision.decide(DecisionStatus.ACCEPTED_AS_IS, null, UUID.randomUUID()))
            .isInstanceOf(AiDecisionAlreadyRecordedConflictException.class);
    }

    @Test
    void should_recordFeedback_when_decisionAlreadyMade() {
        AiJobDecision decision = AiJobDecision.createPending(UUID.randomUUID(), UUID.randomUUID());
        decision.decide(DecisionStatus.ACCEPTED_AS_IS, null, UUID.randomUUID());

        decision.recordFeedback(AccuracyFeedback.ACCURATE);

        assertThat(decision.getAccuracyFeedback()).isEqualTo(AccuracyFeedback.ACCURATE);
        assertThat(decision.getFeedbackAt()).isNotNull();
    }

    @Test
    void should_throwNotYetMade_when_recordingFeedbackBeforeDecision() {
        AiJobDecision decision = AiJobDecision.createPending(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> decision.recordFeedback(AccuracyFeedback.ACCURATE))
            .isInstanceOf(AiDecisionNotYetMadeException.class);
    }

    @Test
    void should_throwAlreadyRecorded_when_recordingFeedbackTwice() {
        AiJobDecision decision = AiJobDecision.createPending(UUID.randomUUID(), UUID.randomUUID());
        decision.decide(DecisionStatus.ACCEPTED_AS_IS, null, UUID.randomUUID());
        decision.recordFeedback(AccuracyFeedback.INACCURATE);

        assertThatThrownBy(() -> decision.recordFeedback(AccuracyFeedback.ACCURATE))
            .isInstanceOf(AiDecisionAlreadyRecordedConflictException.class);
    }

    @Test
    void should_throwMissingAppliedContent_when_acceptedWithEditsHasBlankContent() {
        AiJobDecision decision = AiJobDecision.createPending(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> decision.decide(DecisionStatus.ACCEPTED_WITH_EDITS, "   ", UUID.randomUUID()))
            .isInstanceOf(AiDecisionMissingAppliedContentException.class);
    }
}
