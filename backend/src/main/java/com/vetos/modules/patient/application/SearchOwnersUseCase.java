package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.OwnerSearchResult;
import com.vetos.modules.patient.domain.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SearchOwnersUseCase {

    private final OwnerRepository ownerRepository;

    @Transactional(readOnly = true)
    public List<OwnerSearchResult> execute(UUID tenantId, String query) {
        return ownerRepository.searchByTenantAndQuery(tenantId, query).stream()
            .map(o -> new OwnerSearchResult(o.getId(), o.getFullName(), o.getPhone()))
            .toList();
    }
}
