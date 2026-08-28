package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PlanRepositoryAdapter implements PlanRepository {

    private final PlanJpaRepository jpaRepository;

    @Override
    public Plan save(Plan plan) { return jpaRepository.save(plan); }

    @Override
    public Optional<Plan> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public Optional<Plan> findByCode(String code) { return jpaRepository.findByCode(code); }

    @Override
    public List<Plan> findAll() { return jpaRepository.findAll(); }

    @Override
    public boolean existsByCode(String code) { return jpaRepository.existsByCode(code); }

    @Override
    public void deleteById(UUID id) { jpaRepository.deleteById(id); }
}
