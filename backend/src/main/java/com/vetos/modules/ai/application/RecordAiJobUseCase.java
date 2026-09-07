package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiJob;
import com.vetos.modules.ai.domain.AiJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecordAiJobUseCase {

    private final AiJobRepository aiJobRepository;

    @Transactional
    public UUID execute(RecordAiJobCommand command) {
        AiJob job = AiJob.create(
            command.tenantId(), command.taskType(), command.encounterId(),
            command.suggestionText(), command.modelName(), command.modelVersion(), command.requestedByStaffUserId()
        );
        AiJob saved = aiJobRepository.save(job);
        return saved.getId();
    }
}
