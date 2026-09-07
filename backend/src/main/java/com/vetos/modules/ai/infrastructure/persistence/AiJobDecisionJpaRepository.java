package com.vetos.modules.ai.infrastructure.persistence;

import com.vetos.modules.ai.domain.AiJobDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface AiJobDecisionJpaRepository extends JpaRepository<AiJobDecision, UUID> {
    Optional<AiJobDecision> findByAiJobId(UUID aiJobId);
}
