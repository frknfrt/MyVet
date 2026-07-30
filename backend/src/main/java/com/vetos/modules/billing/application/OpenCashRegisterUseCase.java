package com.vetos.modules.billing.application;

import com.vetos.modules.billing.domain.CashRegisterSession;
import com.vetos.modules.billing.domain.CashRegisterSessionRepository;
import com.vetos.modules.billing.domain.exception.CashRegisterAlreadyOpenConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OpenCashRegisterUseCase {

    private final CashRegisterSessionRepository cashRegisterSessionRepository;

    @Transactional
    public UUID execute(UUID branchId, UUID staffId, BigDecimal openingBalance, String notes) {
        if (cashRegisterSessionRepository.findOpenByBranchId(branchId).isPresent()) {
            throw new CashRegisterAlreadyOpenConflictException(branchId);
        }
        return cashRegisterSessionRepository.save(CashRegisterSession.open(branchId, staffId, openingBalance, notes)).getId();
    }
}
