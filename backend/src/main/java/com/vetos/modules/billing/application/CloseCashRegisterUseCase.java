package com.vetos.modules.billing.application;

import com.vetos.modules.billing.domain.CashRegisterSession;
import com.vetos.modules.billing.domain.CashRegisterSessionRepository;
import com.vetos.modules.billing.domain.exception.CashRegisterSessionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloseCashRegisterUseCase {

    private final CashRegisterSessionRepository cashRegisterSessionRepository;

    @Transactional
    public void execute(UUID sessionId, UUID staffId, BigDecimal closingBalance, String notes) {
        CashRegisterSession session = cashRegisterSessionRepository.findById(sessionId)
            .orElseThrow(() -> new CashRegisterSessionNotFoundException(sessionId));
        session.close(staffId, closingBalance, notes);
        cashRegisterSessionRepository.save(session);
    }
}
