package com.vetos.modules.ai.application;

import com.vetos.platform.ratelimit.RateLimitProperties;
import com.vetos.platform.ratelimit.TenantRateLimiter;
import com.vetos.modules.ai.domain.*;
import com.vetos.modules.ai.domain.exception.ClinicalFindingsRequiredForDiagnosisException;
import com.vetos.modules.encounter.domain.EncounterClinicalContext;
import com.vetos.modules.encounter.domain.EncounterLookupPort;
import com.vetos.modules.encounter.domain.PastEncounterSummary;
import com.vetos.platform.quota.MonthlyQuotaExceededException;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import org.junit.jupiter.api.BeforeEach;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import com.vetos.modules.ai.domain.exception.AiBusyServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
    @Mock private UsageQuotaPort quota;

    private GenerateDiagnosisSuggestionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateDiagnosisSuggestionUseCase(
            encounterLookupPort, diagnosisSuggestionPort, recordAiJobUseCase, "ollama", quota, new AiCallGate(BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(4).build()), new TenantRateLimiter(new RateLimitProperties(600, 10, 2000)))
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
        verifyNoInteractions(quota);
    }

    private UUID stubContext() {
        UUID encounterId = UUID.randomUUID();
        EncounterClinicalContext context = new EncounterClinicalContext(
            encounterId, UUID.randomUUID(), "", "Kusma", "", "", "", List.of()
        );
        when(encounterLookupPort.findClinicalContext(encounterId, 5)).thenReturn(context);
        return encounterId;
    }

    private DiagnosisSuggestionResult run(UUID tenantId, UUID encounterId) {
        return useCase.execute(new GenerateDiagnosisSuggestionCommand(tenantId, encounterId, UUID.randomUUID()));
    }

    @Test
    void should_reserveBeforeCalling_and_keep_when_modelConnected() {
        UUID tenantId = UUID.randomUUID();
        UUID encounterId = stubContext();
        when(diagnosisSuggestionPort.generate(any())).thenReturn(new DiagnosisSuggestionDraft("metin", true, "v"));
        when(recordAiJobUseCase.execute(any())).thenReturn(UUID.randomUUID());

        run(tenantId, encounterId);

        InOrder order = inOrder(quota, diagnosisSuggestionPort);
        order.verify(quota).reserve(tenantId, UsageKind.AI, 1);
        order.verify(diagnosisSuggestionPort).generate(any());
        verify(quota, never()).release(any(), any(), anyInt());
    }

    @Test
    void should_release_when_modelNotConnected() {
        UUID tenantId = UUID.randomUUID();
        UUID encounterId = stubContext();
        when(diagnosisSuggestionPort.generate(any())).thenReturn(new DiagnosisSuggestionDraft("", false, "v"));
        when(recordAiJobUseCase.execute(any())).thenReturn(UUID.randomUUID());

        run(tenantId, encounterId);

        verify(quota).release(tenantId, UsageKind.AI, 1);
        verify(recordAiJobUseCase).execute(any());
    }

    @Test
    void should_release_and_rethrow_when_modelThrows() {
        UUID tenantId = UUID.randomUUID();
        UUID encounterId = stubContext();
        when(diagnosisSuggestionPort.generate(any())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> run(tenantId, encounterId)).hasMessage("boom");

        verify(quota).release(tenantId, UsageKind.AI, 1);
    }

    @Test
    void should_notCallModel_when_quotaExhausted() {
        UUID tenantId = UUID.randomUUID();
        UUID encounterId = stubContext();
        doThrow(new MonthlyQuotaExceededException(UsageKind.AI, 30, 30, 1)).when(quota).reserve(tenantId, UsageKind.AI, 1);

        assertThatThrownBy(() -> run(tenantId, encounterId)).isInstanceOf(MonthlyQuotaExceededException.class);

        verifyNoInteractions(diagnosisSuggestionPort, recordAiJobUseCase);
    }

    @Test
    void should_notReserveQuota_when_bulkheadFull() {
        AiCallGate fullGate = mock(AiCallGate.class);
        when(fullGate.run(any(), any())).thenThrow(new AiBusyServiceUnavailableException());
        UUID encounterId = stubContext();
        GenerateDiagnosisSuggestionUseCase gated = new GenerateDiagnosisSuggestionUseCase(
            encounterLookupPort, diagnosisSuggestionPort, recordAiJobUseCase, "ollama", quota, fullGate
        );

        assertThatThrownBy(() -> gated.execute(new GenerateDiagnosisSuggestionCommand(UUID.randomUUID(), encounterId, UUID.randomUUID()))).isInstanceOf(AiBusyServiceUnavailableException.class);

        verifyNoInteractions(quota);
        verifyNoInteractions(diagnosisSuggestionPort, recordAiJobUseCase);
    }
}
