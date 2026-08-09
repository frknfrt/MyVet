package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.exception.PlanNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeletePlanUseCase {

    private final PlanRepository planRepository;

    @Transactional
    public void execute(UUID planId) {
        planRepository.findById(planId).orElseThrow(() -> new PlanNotFoundException(planId));
        planRepository.deleteById(planId);
    }
}
