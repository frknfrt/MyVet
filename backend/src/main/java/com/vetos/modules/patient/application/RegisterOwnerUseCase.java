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
        owner.updateDetails(
            command.middleName(), command.secondaryPhone(), command.city(), command.district(),
            command.occupation(), command.referralSource(), command.clientDiscount(), command.criticalAlert(),
            command.notes(), command.smsConsent(), command.whatsappConsent(), command.notificationConsent(),
            command.protocolNumber()
        );
        return ownerRepository.save(owner).getId();
    }
}
