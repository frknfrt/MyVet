package com.vetos.modules.lab.application;

import com.vetos.modules.lab.application.dto.RequestLabResultCommand;
import com.vetos.modules.lab.domain.LabResult;
import com.vetos.modules.lab.domain.LabResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RequestLabResultUseCase {

    private final LabResultRepository labResultRepository;

    @Transactional
    public UUID execute(RequestLabResultCommand command) {
        LabResult result = LabResult.request(
            command.tenantId(), command.patientId(), command.orderingStaffId(), command.testName(), command.notes()
        );
        return labResultRepository.save(result).getId();
    }
}
