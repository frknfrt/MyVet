package com.vetos.modules.billing.infrastructure.persistence;

import com.vetos.modules.billing.domain.CashRegisterSession;
import com.vetos.modules.billing.domain.CashRegisterStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CashRegisterSessionJpaRepository extends JpaRepository<CashRegisterSession, UUID> {
    Optional<CashRegisterSession> findByBranchIdAndStatus(UUID branchId, CashRegisterStatus status);
    List<CashRegisterSession> findByBranchId(UUID branchId);
}
