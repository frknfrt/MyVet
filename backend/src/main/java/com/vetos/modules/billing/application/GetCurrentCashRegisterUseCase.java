package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.CashRegisterSessionDetail;
import com.vetos.modules.billing.domain.CashRegisterSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetCurrentCashRegisterUseCase {

    private final CashRegisterSessionRepository cashRegisterSessionRepository;

    @Transactional(readOnly = true)
    public Optional<CashRegisterSessionDetail> execute(UUID branchId) {
        return cashRegisterSessionRepository.findOpenByBranchId(branchId).map(this::toDetail);
    }

    CashRegisterSessionDetail toDetail(com.vetos.modules.billing.domain.CashRegisterSession s) {
        return new CashRegisterSessionDetail(
            s.getId(), s.getBranchId(), s.getOpenedByStaffId(), s.getOpeningBalance(), s.getOpenedAt(),
            s.getClosedByStaffId(), s.getClosingBalance(), s.getClosedAt(), s.getStatus(), s.getNotes()
        );
    }
}
