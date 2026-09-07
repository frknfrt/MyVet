package com.vetos.modules.ai.infrastructure.persistence;

import com.vetos.modules.ai.domain.AiJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface AiJobJpaRepository extends JpaRepository<AiJob, UUID> {
}
