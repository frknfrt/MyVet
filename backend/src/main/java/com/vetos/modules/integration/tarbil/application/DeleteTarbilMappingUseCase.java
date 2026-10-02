package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilValueMapping;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilValueMappingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeleteTarbilMappingUseCase {

    private final TarbilValueMappingRepository repository;

    @Transactional
    public void execute(UUID tenantId, UUID mappingId) {
        TarbilValueMapping mapping = repository.findById(mappingId)
            .filter(m -> m.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilValueMappingNotFoundException(mappingId));
        repository.delete(mapping);
    }
}
