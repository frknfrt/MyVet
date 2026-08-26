package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.StaffShiftTemplate;
import com.vetos.modules.tenant.domain.StaffShiftTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class StaffShiftTemplateRepositoryAdapter implements StaffShiftTemplateRepository {

    private final StaffShiftTemplateJpaRepository jpaRepository;

    @Override
    public List<StaffShiftTemplate> findByStaffUserId(UUID staffUserId) { return jpaRepository.findByStaffUserId(staffUserId); }

    @Override
    public void deleteByStaffUserId(UUID staffUserId) {
        jpaRepository.deleteByStaffUserId(staffUserId);
        jpaRepository.flush();
    }

    @Override
    public List<StaffShiftTemplate> saveAll(List<StaffShiftTemplate> shifts) { return jpaRepository.saveAll(shifts); }
}
