package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListPlansUseCase {

    private final PlanRepository planRepository;

    @Transactional(readOnly = true)
    public List<Plan> execute() {
        return planRepository.findAll();
    }
}
