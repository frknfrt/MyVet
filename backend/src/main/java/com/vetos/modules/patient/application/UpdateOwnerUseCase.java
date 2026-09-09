package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.UpdateOwnerCommand;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import com.vetos.modules.patient.domain.exception.OwnerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateOwnerUseCase {

    private final OwnerRepository ownerRepository;

    @Transactional
    public void execute(UpdateOwnerCommand command) {
        Owner owner = ownerRepository.findById(command.ownerId())
            .orElseThrow(() -> new OwnerNotFoundException(command.ownerId()));
        owner.updateFullName(command.fullName());
        owner.updateContactInfo(command.phone(), command.email(), command.address());
        owner.updateDetails(
            command.middleName(), command.secondaryPhone(), command.city(), command.district(),
            command.occupation(), command.referralSource(), command.clientDiscount(), command.criticalAlert(),
            command.notes(), command.smsConsent(), command.whatsappConsent(), command.notificationConsent(),
            command.protocolNumber()
        );
        ownerRepository.save(owner);
    }
}
