package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.StaffInvite;
import com.vetos.modules.tenant.domain.StaffInviteRepository;
import com.vetos.modules.tenant.domain.StaffInviteStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class StaffInviteRepositoryAdapter implements StaffInviteRepository {

    private final StaffInviteJpaRepository jpaRepository;

    @Override
    public StaffInvite save(StaffInvite invite) {
        return jpaRepository.save(invite);
    }

    @Override
    public Optional<StaffInvite> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<StaffInvite> findByToken(String token) {
        return jpaRepository.findByToken(token);
    }

    @Override
    public boolean existsByEmailAndStatus(String email, StaffInviteStatus status) {
        return jpaRepository.existsByEmailAndStatus(email, status);
    }

    @Override
    public List<StaffInvite> findByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }
}
