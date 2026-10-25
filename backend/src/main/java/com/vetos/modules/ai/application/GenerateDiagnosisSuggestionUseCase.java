package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiTaskType;
import com.vetos.modules.ai.domain.DiagnosisSuggestionDraft;
import com.vetos.modules.ai.domain.DiagnosisSuggestionInput;
import com.vetos.modules.ai.domain.DiagnosisSuggestionPort;
import com.vetos.modules.ai.domain.exception.ClinicalFindingsRequiredForDiagnosisException;
import com.vetos.modules.encounter.domain.EncounterClinicalContext;
import com.vetos.modules.encounter.domain.EncounterLookupPort;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
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

    /** Tavanın bir fazlası: PatientHistoryAssembler kısaltıldığını anlayabilsin. */
    private static final int HISTORY_LIMIT = PatientHistoryAssembler.MAX_ENCOUNTERS + 1;

    private final EncounterLookupPort encounterLookupPort;
    private final DiagnosisSuggestionPort diagnosisSuggestionPort;
    private final RecordAiJobUseCase recordAiJobUseCase;
    private final String providerName;
    private final UsageQuotaPort usageQuotaPort;
    private final AiCallGate aiCallGate;
    private final PatientHistoryAssembler patientHistoryAssembler;

    public GenerateDiagnosisSuggestionUseCase(
        EncounterLookupPort encounterLookupPort,
        DiagnosisSuggestionPort diagnosisSuggestionPort,
        RecordAiJobUseCase recordAiJobUseCase,
        @Value("${ai.provider:ollama}") String providerName,
        UsageQuotaPort usageQuotaPort,
        PatientHistoryAssembler patientHistoryAssembler,
        AiCallGate aiCallGate
    ) {
        this.encounterLookupPort = encounterLookupPort;
        this.diagnosisSuggestionPort = diagnosisSuggestionPort;
        this.recordAiJobUseCase = recordAiJobUseCase;
        this.providerName = providerName;
        this.usageQuotaPort = usageQuotaPort;
        this.aiCallGate = aiCallGate;
        this.patientHistoryAssembler = patientHistoryAssembler;
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
            PatientHistoryFormatter.format(patientHistoryAssembler.assemble(context))
        );

        // Kota ayirma kapinin icinde: AI yogunsa hak dusmez.
        DiagnosisSuggestionDraft draft = aiCallGate.run(command.tenantId(), () -> {
            usageQuotaPort.reserve(command.tenantId(), UsageKind.AI, 1);
            DiagnosisSuggestionDraft generated;
            try {
                generated = diagnosisSuggestionPort.generate(input);
            } catch (RuntimeException e) {
                usageQuotaPort.release(command.tenantId(), UsageKind.AI, 1);
                throw e;
            }
            if (!generated.modelConnected()) {
                usageQuotaPort.release(command.tenantId(), UsageKind.AI, 1);
            }
            return generated;
        });

        UUID aiJobId = recordAiJobUseCase.execute(new RecordAiJobCommand(
            command.tenantId(), AiTaskType.DIAGNOSIS_SUGGESTION, command.encounterId(),
            draft.suggestionText(), providerName, draft.modelVersion(), command.requestedByStaffUserId()
        ));

        return new DiagnosisSuggestionResult(aiJobId, draft.suggestionText(), draft.modelConnected());
    }
}
