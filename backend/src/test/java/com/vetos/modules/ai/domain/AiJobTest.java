package com.vetos.modules.ai.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class AiJobTest {

    @Test
    void should_setAllFieldsAndCreatedAt_when_created() {
        UUID tenantId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();

        AiJob job = AiJob.create(
            tenantId, AiTaskType.TREATMENT_RECOMMENDATION, encounterId,
            "Sivi tedavisi onerilir", "ollama", "llama3.1:8b", staffUserId
        );

        assertThat(job.getTenantId()).isEqualTo(tenantId);
        assertThat(job.getTaskType()).isEqualTo(AiTaskType.TREATMENT_RECOMMENDATION);
        assertThat(job.getEncounterId()).isEqualTo(encounterId);
        assertThat(job.getSuggestionText()).isEqualTo("Sivi tedavisi onerilir");
        assertThat(job.getModelName()).isEqualTo("ollama");
        assertThat(job.getModelVersion()).isEqualTo("llama3.1:8b");
        assertThat(job.getRequestedByStaffUserId()).isEqualTo(staffUserId);
        assertThat(job.getCreatedAt()).isNotNull();
    }
}
