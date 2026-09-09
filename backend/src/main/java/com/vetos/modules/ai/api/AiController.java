package com.vetos.modules.ai.api;

import com.vetos.modules.ai.api.dto.*;
import com.vetos.modules.ai.application.*;
import com.vetos.platform.security.AuthenticatedStaffUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('VET', 'ADMIN')")
public class AiController {

    private final GenerateSoapDraftUseCase generateSoapDraftUseCase;
    private final GenerateTreatmentRecommendationUseCase generateTreatmentRecommendationUseCase;
    private final GenerateDiagnosisSuggestionUseCase generateDiagnosisSuggestionUseCase;
    private final RecordAiJobDecisionUseCase recordAiJobDecisionUseCase;

    @PostMapping("/soap-drafts")
    public SoapDraftResponse generateSoapDraft(@RequestBody @Valid GenerateSoapDraftRequest request) {
        return SoapDraftResponse.from(generateSoapDraftUseCase.execute(request.transcript()));
    }

    @PostMapping("/treatment-recommendations")
    public TreatmentRecommendationResponse generateTreatmentRecommendation(
        @RequestBody @Valid GenerateTreatmentRecommendationRequest request,
        @AuthenticationPrincipal AuthenticatedStaffUser principal
    ) {
        TreatmentRecommendationResult result = generateTreatmentRecommendationUseCase.execute(
            new GenerateTreatmentRecommendationCommand(principal.tenantId(), request.encounterId(), principal.staffUserId())
        );
        return TreatmentRecommendationResponse.from(result);
    }

    @PostMapping("/treatment-recommendations/{aiJobId}/decision")
    public void decideTreatmentRecommendation(
        @PathVariable UUID aiJobId,
        @RequestBody @Valid DecideTreatmentRecommendationRequest request,
        @AuthenticationPrincipal AuthenticatedStaffUser principal
    ) {
        recordAiJobDecisionUseCase.execute(new RecordAiJobDecisionCommand(
            principal.tenantId(), aiJobId, request.status(), request.appliedContent(), principal.staffUserId()
        ));
    }

    @PostMapping("/diagnosis-suggestions")
    public DiagnosisSuggestionResponse generateDiagnosisSuggestion(
        @RequestBody @Valid GenerateDiagnosisSuggestionRequest request,
        @AuthenticationPrincipal AuthenticatedStaffUser principal
    ) {
        DiagnosisSuggestionResult result = generateDiagnosisSuggestionUseCase.execute(
            new GenerateDiagnosisSuggestionCommand(principal.tenantId(), request.encounterId(), principal.staffUserId())
        );
        return DiagnosisSuggestionResponse.from(result);
    }

    @PostMapping("/diagnosis-suggestions/{aiJobId}/decision")
    public void decideDiagnosisSuggestion(
        @PathVariable UUID aiJobId,
        @RequestBody @Valid DecideTreatmentRecommendationRequest request,
        @AuthenticationPrincipal AuthenticatedStaffUser principal
    ) {
        recordAiJobDecisionUseCase.execute(new RecordAiJobDecisionCommand(
            principal.tenantId(), aiJobId, request.status(), request.appliedContent(), principal.staffUserId()
        ));
    }
}
