package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiJob;
import com.vetos.modules.ai.domain.AiJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecordAiJobUseCase {

    private final AiJobRepository aiJobRepository;

    public UUID execute(RecordAiJobCommand command) {
        AiJob job = AiJob.create(
            command.tenantId(), command.taskType(), command.encounterId(),
            command.suggestionText(), command.modelName(), command.modelVersion(), command.requestedByStaffUserId()
        );
        aiJobRepository.save(job);
        return job.getId();
    }
}
