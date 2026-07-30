package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class StaffUserRepositoryAdapter implements StaffUserRepository {

    private final StaffUserJpaRepository jpaRepository;

    @Override
    public StaffUser save(StaffUser staffUser) { return jpaRepository.save(staffUser); }

    @Override
    public Optional<StaffUser> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public Optional<StaffUser> findByEmail(String email) { return jpaRepository.findByEmail(email); }

    @Override
    public boolean existsByEmail(String email) { return jpaRepository.existsByEmail(email); }

    @Override
    public List<StaffUser> findByBranchId(UUID branchId) { return jpaRepository.findByBranchId(branchId); }
}
