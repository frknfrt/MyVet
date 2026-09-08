package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.*;
import com.vetos.modules.ai.domain.exception.AssessmentRequiredForRecommendationException;
import com.vetos.modules.encounter.domain.EncounterClinicalContext;
import com.vetos.modules.encounter.domain.EncounterLookupPort;
import com.vetos.modules.encounter.domain.PastEncounterSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerateTreatmentRecommendationUseCaseTest {

    @Mock private EncounterLookupPort encounterLookupPort;
    @Mock private TreatmentRecommendationPort treatmentRecommendationPort;
    @Mock private RecordAiJobUseCase recordAiJobUseCase;

    private GenerateTreatmentRecommendationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateTreatmentRecommendationUseCase(
            encounterLookupPort, treatmentRecommendationPort, recordAiJobUseCase, "llama3.1:8b"
        );
    }

    @Test
    void should_recordAiJobAndReturnResult_when_assessmentPresent() {
        UUID tenantId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();
        UUID aiJobId = UUID.randomUUID();
        EncounterClinicalContext context = new EncounterClinicalContext(
            encounterId, UUID.randomUUID(), "Hafif gastrit supheli",
            List.of(new PastEncounterSummary(Instant.now(), "Gecmis degerlendirme", "Gecmis plan"))
        );
        when(encounterLookupPort.findClinicalContext(encounterId, 5)).thenReturn(context);
        TreatmentRecommendationDraft draft = new TreatmentRecommendationDraft("Diyet degisikligi onerilir", true);
        when(treatmentRecommendationPort.generate(any())).thenReturn(draft);
        when(recordAiJobUseCase.execute(any())).thenReturn(aiJobId);

        TreatmentRecommendationResult result = useCase.execute(
            new GenerateTreatmentRecommendationCommand(tenantId, encounterId, staffUserId)
        );

        assertThat(result.aiJobId()).isEqualTo(aiJobId);
        assertThat(result.suggestionText()).isEqualTo("Diyet degisikligi onerilir");
        assertThat(result.modelConnected()).isTrue();

        ArgumentCaptor<RecordAiJobCommand> captor = ArgumentCaptor.forClass(RecordAiJobCommand.class);
        verify(recordAiJobUseCase).execute(captor.capture());
        RecordAiJobCommand recorded = captor.getValue();
        assertThat(recorded.tenantId()).isEqualTo(tenantId);
        assertThat(recorded.taskType()).isEqualTo(AiTaskType.TREATMENT_RECOMMENDATION);
        assertThat(recorded.encounterId()).isEqualTo(encounterId);
        assertThat(recorded.suggestionText()).isEqualTo("Diyet degisikligi onerilir");
        assertThat(recorded.modelName()).isEqualTo("ollama");
        assertThat(recorded.modelVersion()).isEqualTo("llama3.1:8b");
        assertThat(recorded.requestedByStaffUserId()).isEqualTo(staffUserId);
    }

    @Test
    void should_throwAssessmentRequired_when_assessmentIsBlank() {
        UUID encounterId = UUID.randomUUID();
        EncounterClinicalContext context = new EncounterClinicalContext(
            encounterId, UUID.randomUUID(), "   ", List.of()
        );
        when(encounterLookupPort.findClinicalContext(encounterId, 5)).thenReturn(context);

        assertThatThrownBy(() -> useCase.execute(
            new GenerateTreatmentRecommendationCommand(UUID.randomUUID(), encounterId, UUID.randomUUID())
        )).isInstanceOf(AssessmentRequiredForRecommendationException.class);

        verifyNoInteractions(treatmentRecommendationPort, recordAiJobUseCase);
    }
}
