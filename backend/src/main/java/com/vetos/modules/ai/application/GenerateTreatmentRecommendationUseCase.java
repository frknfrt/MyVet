package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.*;
import com.vetos.modules.ai.domain.exception.AssessmentRequiredForRecommendationException;
import com.vetos.modules.encounter.domain.EncounterClinicalContext;
import com.vetos.modules.encounter.domain.EncounterLookupPort;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GenerateTreatmentRecommendationUseCase {

    /** Tavanın bir fazlası: PatientHistoryAssembler kısaltıldığını anlayabilsin. */
    private static final int HISTORY_LIMIT = PatientHistoryAssembler.MAX_ENCOUNTERS + 1;

    private final EncounterLookupPort encounterLookupPort;
    private final TreatmentRecommendationPort treatmentRecommendationPort;
    private final RecordAiJobUseCase recordAiJobUseCase;
    private final String providerName;
    private final UsageQuotaPort usageQuotaPort;
    private final AiCallGate aiCallGate;
    private final PatientHistoryAssembler patientHistoryAssembler;

    public GenerateTreatmentRecommendationUseCase(
        EncounterLookupPort encounterLookupPort,
        TreatmentRecommendationPort treatmentRecommendationPort,
        RecordAiJobUseCase recordAiJobUseCase,
        @Value("${ai.provider:ollama}") String providerName,
        UsageQuotaPort usageQuotaPort,
        PatientHistoryAssembler patientHistoryAssembler,
        AiCallGate aiCallGate
    ) {
        this.encounterLookupPort = encounterLookupPort;
        this.treatmentRecommendationPort = treatmentRecommendationPort;
        this.recordAiJobUseCase = recordAiJobUseCase;
        this.providerName = providerName;
        this.usageQuotaPort = usageQuotaPort;
        this.aiCallGate = aiCallGate;
        this.patientHistoryAssembler = patientHistoryAssembler;
    }

    public TreatmentRecommendationResult execute(GenerateTreatmentRecommendationCommand command) {
        EncounterClinicalContext context = encounterLookupPort.findClinicalContext(command.encounterId(), HISTORY_LIMIT);
        if (context.currentAssessment() == null || context.currentAssessment().isBlank()) {
            throw new AssessmentRequiredForRecommendationException(command.encounterId());
        }

        TreatmentRecommendationInput input = new TreatmentRecommendationInput(
            context.currentAssessment(),
            PatientHistoryFormatter.format(patientHistoryAssembler.assemble(context))
        );
        // Kota ayirma kapinin icinde: AI yogunsa hak dusmez.
        TreatmentRecommendationDraft draft = aiCallGate.run(command.tenantId(), () -> {
            usageQuotaPort.reserve(command.tenantId(), UsageKind.AI, 1);
            TreatmentRecommendationDraft generated;
            try {
                generated = treatmentRecommendationPort.generate(input);
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
            command.tenantId(), AiTaskType.TREATMENT_RECOMMENDATION, command.encounterId(),
            draft.suggestionText(), providerName, draft.modelVersion(), command.requestedByStaffUserId()
        ));

        return new TreatmentRecommendationResult(aiJobId, draft.suggestionText(), draft.modelConnected());
    }
}
