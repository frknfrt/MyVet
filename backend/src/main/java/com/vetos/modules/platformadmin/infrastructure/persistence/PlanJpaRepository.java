package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PlanJpaRepository extends JpaRepository<Plan, UUID> {
    boolean existsByCode(String code);
    Optional<Plan> findByCode(String code);
}
