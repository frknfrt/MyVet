package com.vetos.modules.billing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashRegisterSessionRepository {
    CashRegisterSession save(CashRegisterSession session);
    Optional<CashRegisterSession> findById(UUID id);
    Optional<CashRegisterSession> findOpenByBranchId(UUID branchId);
    List<CashRegisterSession> findByBranchId(UUID branchId);
}
