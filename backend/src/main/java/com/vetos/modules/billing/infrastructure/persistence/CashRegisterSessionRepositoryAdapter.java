package com.vetos.modules.billing.infrastructure.persistence;

import com.vetos.modules.billing.domain.CashRegisterSession;
import com.vetos.modules.billing.domain.CashRegisterSessionRepository;
import com.vetos.modules.billing.domain.CashRegisterStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class CashRegisterSessionRepositoryAdapter implements CashRegisterSessionRepository {

    private final CashRegisterSessionJpaRepository jpaRepository;

    @Override
    public CashRegisterSession save(CashRegisterSession session) { return jpaRepository.save(session); }

    @Override
    public Optional<CashRegisterSession> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public Optional<CashRegisterSession> findOpenByBranchId(UUID branchId) {
        return jpaRepository.findByBranchIdAndStatus(branchId, CashRegisterStatus.OPEN);
    }

    @Override
    public List<CashRegisterSession> findByBranchId(UUID branchId) { return jpaRepository.findByBranchId(branchId); }
}
