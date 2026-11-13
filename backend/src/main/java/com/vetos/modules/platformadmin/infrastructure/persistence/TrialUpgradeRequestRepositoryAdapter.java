package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.TrialUpgradeRequest;
import com.vetos.modules.platformadmin.domain.TrialUpgradeRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TrialUpgradeRequestRepositoryAdapter implements TrialUpgradeRequestRepository {

    private final TrialUpgradeRequestJpaRepository jpaRepository;

    @Override
    public TrialUpgradeRequest save(TrialUpgradeRequest request) { return jpaRepository.save(request); }

    @Override
    public Optional<TrialUpgradeRequest> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public Optional<TrialUpgradeRequest> findByIdForUpdate(UUID id) { return jpaRepository.findByIdForUpdate(id); }
}
