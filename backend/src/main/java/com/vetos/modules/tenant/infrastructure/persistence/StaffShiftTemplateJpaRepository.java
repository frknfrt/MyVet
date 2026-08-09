package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.StaffShiftTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface StaffShiftTemplateJpaRepository extends JpaRepository<StaffShiftTemplate, UUID> {
    List<StaffShiftTemplate> findByStaffUserId(UUID staffUserId);
    void deleteByStaffUserId(UUID staffUserId);
}
