package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.exception.StaffUserNotFoundException;
import com.vetos.modules.tenant.application.dto.StaffShiftEntry;
import com.vetos.modules.tenant.domain.StaffShiftTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetStaffShiftTemplateUseCase {

    private final StaffUserRepository staffUserRepository;
    private final StaffShiftTemplateRepository staffShiftTemplateRepository;

    @Transactional(readOnly = true)
    public List<StaffShiftEntry> execute(UUID staffUserId) {
        // Personel kiraci filtreli: baska klinigin personeli 404 (vardiya tablosunda tenant_id yok).
        staffUserRepository.findById(staffUserId).orElseThrow(() -> new StaffUserNotFoundException(staffUserId));
        return staffShiftTemplateRepository.findByStaffUserId(staffUserId).stream()
            .map(shift -> new StaffShiftEntry(shift.getDayOfWeek(), shift.getStartsAt(), shift.getEndsAt()))
            .toList();
    }
}
