package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.*;
import com.vetos.modules.ai.domain.exception.AssessmentRequiredForRecommendationException;
import com.vetos.modules.encounter.domain.EncounterClinicalContext;
import com.vetos.modules.encounter.domain.EncounterLookupPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GenerateTreatmentRecommendationUseCase {

    private static final int HISTORY_LIMIT = 5;

    private final EncounterLookupPort encounterLookupPort;
    private final TreatmentRecommendationPort treatmentRecommendationPort;
    private final RecordAiJobUseCase recordAiJobUseCase;
    private final String ollamaModelName;

    public GenerateTreatmentRecommendationUseCase(
        EncounterLookupPort encounterLookupPort,
        TreatmentRecommendationPort treatmentRecommendationPort,
        RecordAiJobUseCase recordAiJobUseCase,
        @Value("${ai.ollama.model:llama3.1}") String ollamaModelName
    ) {
        this.encounterLookupPort = encounterLookupPort;
        this.treatmentRecommendationPort = treatmentRecommendationPort;
        this.recordAiJobUseCase = recordAiJobUseCase;
        this.ollamaModelName = ollamaModelName;
    }

    public TreatmentRecommendationResult execute(GenerateTreatmentRecommendationCommand command) {
        EncounterClinicalContext context = encounterLookupPort.findClinicalContext(command.encounterId(), HISTORY_LIMIT);
        if (context.currentAssessment() == null || context.currentAssessment().isBlank()) {
            throw new AssessmentRequiredForRecommendationException(command.encounterId());
        }

        TreatmentRecommendationInput input = new TreatmentRecommendationInput(
            context.currentAssessment(),
            context.recentHistory().stream()
                .map(h -> new HistoryEntry(h.date().toString(), h.assessment(), h.plan()))
                .toList()
        );
        TreatmentRecommendationDraft draft = treatmentRecommendationPort.generate(input);

        UUID aiJobId = recordAiJobUseCase.execute(new RecordAiJobCommand(
            command.tenantId(), AiTaskType.TREATMENT_RECOMMENDATION, command.encounterId(),
            draft.suggestionText(), "ollama", ollamaModelName, command.requestedByStaffUserId()
        ));

        return new TreatmentRecommendationResult(aiJobId, draft.suggestionText(), draft.modelConnected());
    }
}
