package com.vetos.modules.appointment.application;

import com.vetos.modules.appointment.application.dto.CreateServiceTypeCommand;
import com.vetos.modules.appointment.domain.ServiceType;
import com.vetos.modules.appointment.domain.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateServiceTypeUseCase {

    private final ServiceTypeRepository serviceTypeRepository;

    @Transactional
    public UUID execute(CreateServiceTypeCommand command) {
        ServiceType serviceType = ServiceType.create(
            command.tenantId(), command.name(), command.defaultDurationMin(), command.defaultPrice()
        );
        return serviceTypeRepository.save(serviceType).getId();
    }
}
