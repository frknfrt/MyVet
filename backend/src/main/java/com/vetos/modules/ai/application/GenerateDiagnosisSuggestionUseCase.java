package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiTaskType;
import com.vetos.modules.ai.domain.DiagnosisSuggestionDraft;
import com.vetos.modules.ai.domain.DiagnosisSuggestionInput;
import com.vetos.modules.ai.domain.DiagnosisSuggestionPort;
import com.vetos.modules.ai.domain.HistoryEntry;
import com.vetos.modules.ai.domain.exception.ClinicalFindingsRequiredForDiagnosisException;
import com.vetos.modules.encounter.domain.EncounterClinicalContext;
import com.vetos.modules.encounter.domain.EncounterLookupPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * GenerateTreatmentRecommendationUseCase ile ayni desen (bkz. o sinif) --
 * tek fark: Assessment yerine Subjective/Objective doluluğu aranir (tani
 * destegi, hekim Assessment'i yazmadan ONCE calisir), ve sonuc Plan yerine
 * Assessment alanina uygulanmak uzere donuyor (bkz. AiController/frontend).
 */
@Service
public class GenerateDiagnosisSuggestionUseCase {

    private static final int HISTORY_LIMIT = 5;

    private final EncounterLookupPort encounterLookupPort;
    private final DiagnosisSuggestionPort diagnosisSuggestionPort;
    private final RecordAiJobUseCase recordAiJobUseCase;
    private final String providerName;

    public GenerateDiagnosisSuggestionUseCase(
        EncounterLookupPort encounterLookupPort,
        DiagnosisSuggestionPort diagnosisSuggestionPort,
        RecordAiJobUseCase recordAiJobUseCase,
        @Value("${ai.provider:ollama}") String providerName
    ) {
        this.encounterLookupPort = encounterLookupPort;
        this.diagnosisSuggestionPort = diagnosisSuggestionPort;
        this.recordAiJobUseCase = recordAiJobUseCase;
        this.providerName = providerName;
    }

    public DiagnosisSuggestionResult execute(GenerateDiagnosisSuggestionCommand command) {
        EncounterClinicalContext context = encounterLookupPort.findClinicalContext(command.encounterId(), HISTORY_LIMIT);
        boolean hasSubjective = context.subjective() != null && !context.subjective().isBlank();
        boolean hasObjective = context.objective() != null && !context.objective().isBlank();
        if (!hasSubjective && !hasObjective) {
            throw new ClinicalFindingsRequiredForDiagnosisException(command.encounterId());
        }

        DiagnosisSuggestionInput input = new DiagnosisSuggestionInput(
            context.subjective(),
            context.objective(),
            context.physicalExamSummary(),
            context.vitalsSummary(),
            context.recentHistory().stream()
                .map(h -> new HistoryEntry(h.date().toString(), h.assessment(), h.plan()))
                .toList()
        );

        DiagnosisSuggestionDraft draft = diagnosisSuggestionPort.generate(input);

        UUID aiJobId = recordAiJobUseCase.execute(new RecordAiJobCommand(
            command.tenantId(), AiTaskType.DIAGNOSIS_SUGGESTION, command.encounterId(),
            draft.suggestionText(), providerName, draft.modelVersion(), command.requestedByStaffUserId()
        ));

        return new DiagnosisSuggestionResult(aiJobId, draft.suggestionText(), draft.modelConnected());
    }
}
