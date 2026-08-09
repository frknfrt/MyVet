package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.OwnerCampaignCandidate;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListOwnerCampaignCandidatesUseCase {

    private final OwnerRepository ownerRepository;

    @Transactional(readOnly = true)
    public List<OwnerCampaignCandidate> execute(UUID tenantId, String nameContains, Instant registeredFrom, Instant registeredTo) {
        return ownerRepository.findByTenantIdWithFilters(tenantId, nameContains, registeredFrom, registeredTo).stream()
            .map(this::toCandidate)
            .toList();
    }

    private OwnerCampaignCandidate toCandidate(Owner owner) {
        return new OwnerCampaignCandidate(
            owner.getId(), owner.getFullName(), owner.getPhone(), owner.isSmsConsent(), owner.isWhatsappConsent(), owner.getCreatedAt()
        );
    }
}
