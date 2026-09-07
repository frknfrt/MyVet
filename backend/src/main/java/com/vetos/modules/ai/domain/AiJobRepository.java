package com.vetos.modules.ai.domain;

import java.util.Optional;
import java.util.UUID;

public interface AiJobRepository {
    AiJob save(AiJob job);
    Optional<AiJob> findById(UUID id);
}
