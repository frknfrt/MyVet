package com.vetos.modules.platformadmin.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository {
    Plan save(Plan plan);
    Optional<Plan> findById(UUID id);
    Optional<Plan> findByCode(String code);
    List<Plan> findAll();
    boolean existsByCode(String code);
    void deleteById(UUID id);
}
