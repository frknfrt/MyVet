package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.SetStaffShiftTemplateCommand;
import com.vetos.modules.tenant.domain.StaffShiftTemplate;
import com.vetos.modules.tenant.domain.StaffShiftTemplateRepository;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.exception.StaffUserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SetStaffShiftTemplateUseCase {

    private final StaffUserRepository staffUserRepository;
    private final StaffShiftTemplateRepository staffShiftTemplateRepository;

    @Transactional
    public void execute(SetStaffShiftTemplateCommand command) {
        staffUserRepository.findById(command.staffUserId())
            .orElseThrow(() -> new StaffUserNotFoundException(command.staffUserId()));

        staffShiftTemplateRepository.deleteByStaffUserId(command.staffUserId());
        var shifts = command.entries().stream()
            .map(entry -> StaffShiftTemplate.create(command.staffUserId(), entry.dayOfWeek(), entry.startsAt(), entry.endsAt()))
            .toList();
        staffShiftTemplateRepository.saveAll(shifts);
    }
}
