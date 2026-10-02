package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilMappingSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListTarbilMappingsUseCase {

    private final TarbilValueMappingRepository repository;

    @Transactional(readOnly = true)
    public List<TarbilMappingSummary> execute(UUID tenantId) {
        return repository.findByTenantId(tenantId).stream()
            .map(m -> new TarbilMappingSummary(m.getId(), m.getKind(), m.getVetlyKey(), m.getTarbilFields(), m.getUpdatedAt()))
            .toList();
    }
}
