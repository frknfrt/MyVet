package com.vetos.modules.ai.infrastructure.persistence;

import com.vetos.modules.ai.domain.AiJob;
import com.vetos.modules.ai.domain.AiJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class AiJobRepositoryAdapter implements AiJobRepository {

    private final AiJobJpaRepository jpaRepository;

    @Override
    public AiJob save(AiJob job) {
        return jpaRepository.save(job);
    }

    @Override
    public Optional<AiJob> findById(UUID id) {
        return jpaRepository.findById(id);
    }
}
