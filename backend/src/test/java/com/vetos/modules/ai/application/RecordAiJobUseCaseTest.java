package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiJob;
import com.vetos.modules.ai.domain.AiJobRepository;
import com.vetos.modules.ai.domain.AiTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordAiJobUseCaseTest {

    @Mock private AiJobRepository aiJobRepository;

    private RecordAiJobUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RecordAiJobUseCase(aiJobRepository);
    }

    @Test
    void should_saveAiJobWithGivenFields_when_executed() {
        UUID tenantId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();
        RecordAiJobCommand command = new RecordAiJobCommand(
            tenantId, AiTaskType.TREATMENT_RECOMMENDATION, encounterId,
            "Sivi tedavisi onerilir", "ollama", "llama3.1:8b", staffUserId
        );
        when(aiJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID resultId = useCase.execute(command);

        ArgumentCaptor<AiJob> captor = ArgumentCaptor.forClass(AiJob.class);
        verify(aiJobRepository).save(captor.capture());
        AiJob saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getEncounterId()).isEqualTo(encounterId);
        assertThat(saved.getSuggestionText()).isEqualTo("Sivi tedavisi onerilir");
        assertThat(resultId).isEqualTo(saved.getId());
    }
}
