package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.RegisterOwnerCommand;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegisterOwnerUseCase {

    private final OwnerRepository ownerRepository;

    @Transactional
    public UUID execute(RegisterOwnerCommand command) {
        Owner owner = Owner.register(
            command.tenantId(), command.fullName(), command.phone(), command.email(), command.address()
        );
        owner.setMarketingConsent(command.marketingConsent());
        return ownerRepository.save(owner).getId();
    }
}
