package com.vetos.modules.billing.application;

import com.vetos.modules.billing.domain.CashRegisterSession;
import com.vetos.modules.billing.domain.CashRegisterSessionRepository;
import com.vetos.modules.billing.domain.exception.CashRegisterSessionNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditLabels;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloseCashRegisterUseCase {

    private final CashRegisterSessionRepository cashRegisterSessionRepository;
    private final AuditPort auditPort;

    @Transactional
    public void execute(UUID sessionId, UUID staffId, BigDecimal closingBalance, String notes) {
        CashRegisterSession session = cashRegisterSessionRepository.findById(sessionId)
            .orElseThrow(() -> new CashRegisterSessionNotFoundException(sessionId));
        session.close(staffId, closingBalance, notes);
        cashRegisterSessionRepository.save(session);
        auditPort.record(AuditRecord.of(AuditAction.CASH_REGISTER_CLOSED, "CASH_REGISTER", sessionId,
            null, "Kapanış: " + AuditLabels.money(closingBalance)));
    }
}
