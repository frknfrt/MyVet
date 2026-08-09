package com.vetos.modules.tenant.domain;

import java.util.List;
import java.util.UUID;

public interface StaffShiftTemplateRepository {
    List<StaffShiftTemplate> findByStaffUserId(UUID staffUserId);
    void deleteByStaffUserId(UUID staffUserId);
    List<StaffShiftTemplate> saveAll(List<StaffShiftTemplate> shifts);
}
