package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.OwnerSummary;
import com.vetos.modules.patient.domain.exception.OwnerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class OwnerLookupAdapter implements OwnerLookupPort {

    private final OwnerJpaRepository jpaRepository;

    @Override
    public OwnerSummary findSummaryById(UUID ownerId) {
        Owner owner = jpaRepository.findById(ownerId).orElseThrow(() -> new OwnerNotFoundException(ownerId));
        return new OwnerSummary(
            owner.getId(), owner.getFullName(), owner.getPhone(), owner.getAddress(), owner.getCity(), owner.getDistrict(),
            owner.getNationalIdMasked(), owner.isSmsConsent(), owner.isWhatsappConsent()
        );
    }
}
