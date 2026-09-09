package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.*;
import com.vetos.modules.ai.domain.exception.ClinicalFindingsRequiredForDiagnosisException;
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

/**
 * GenerateTreatmentRecommendationUseCaseTest ile ayni desen -- tek fark:
 * Assessment yerine Subjective/Objective doluluğu araniyor.
 */
@ExtendWith(MockitoExtension.class)
class GenerateDiagnosisSuggestionUseCaseTest {

    @Mock private EncounterLookupPort encounterLookupPort;
    @Mock private DiagnosisSuggestionPort diagnosisSuggestionPort;
    @Mock private RecordAiJobUseCase recordAiJobUseCase;

    private GenerateDiagnosisSuggestionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateDiagnosisSuggestionUseCase(
            encounterLookupPort, diagnosisSuggestionPort, recordAiJobUseCase, "ollama"
        );
    }

    @Test
    void should_recordAiJobAndReturnResult_when_subjectiveOrObjectivePresent() {
        UUID tenantId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();
        UUID aiJobId = UUID.randomUUID();
        EncounterClinicalContext context = new EncounterClinicalContext(
            encounterId, UUID.randomUUID(), "",
            "Sahip kusma bildiriyor", "Karin hassasiyeti", "Nabiz: 185 /dk", "Gastrointestinal: Anormal",
            List.of(new PastEncounterSummary(Instant.now(), "Gecmis degerlendirme", "Gecmis plan"))
        );
        when(encounterLookupPort.findClinicalContext(encounterId, 5)).thenReturn(context);
        DiagnosisSuggestionDraft draft = new DiagnosisSuggestionDraft("Olasi gastroenterit", true, "llama3.1:8b");
        when(diagnosisSuggestionPort.generate(any())).thenReturn(draft);
        when(recordAiJobUseCase.execute(any())).thenReturn(aiJobId);

        DiagnosisSuggestionResult result = useCase.execute(
            new GenerateDiagnosisSuggestionCommand(tenantId, encounterId, staffUserId)
        );

        assertThat(result.aiJobId()).isEqualTo(aiJobId);
        assertThat(result.suggestionText()).isEqualTo("Olasi gastroenterit");
        assertThat(result.modelConnected()).isTrue();

        ArgumentCaptor<RecordAiJobCommand> captor = ArgumentCaptor.forClass(RecordAiJobCommand.class);
        verify(recordAiJobUseCase).execute(captor.capture());
        RecordAiJobCommand recorded = captor.getValue();
        assertThat(recorded.tenantId()).isEqualTo(tenantId);
        assertThat(recorded.taskType()).isEqualTo(AiTaskType.DIAGNOSIS_SUGGESTION);
        assertThat(recorded.encounterId()).isEqualTo(encounterId);
        assertThat(recorded.suggestionText()).isEqualTo("Olasi gastroenterit");
        assertThat(recorded.modelName()).isEqualTo("ollama");
        assertThat(recorded.modelVersion()).isEqualTo("llama3.1:8b");
        assertThat(recorded.requestedByStaffUserId()).isEqualTo(staffUserId);
    }

    @Test
    void should_throwClinicalFindingsRequired_when_subjectiveAndObjectiveAreBothBlank() {
        UUID encounterId = UUID.randomUUID();
        EncounterClinicalContext context = new EncounterClinicalContext(
            encounterId, UUID.randomUUID(), "", "   ", "   ", "", "", List.of()
        );
        when(encounterLookupPort.findClinicalContext(encounterId, 5)).thenReturn(context);

        assertThatThrownBy(() -> useCase.execute(
            new GenerateDiagnosisSuggestionCommand(UUID.randomUUID(), encounterId, UUID.randomUUID())
        )).isInstanceOf(ClinicalFindingsRequiredForDiagnosisException.class);

        verifyNoInteractions(diagnosisSuggestionPort, recordAiJobUseCase);
    }
}
