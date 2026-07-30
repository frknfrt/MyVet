package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.CashRegisterSessionDetail;
import com.vetos.modules.billing.domain.CashRegisterSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListCashRegisterHistoryUseCase {

    private final CashRegisterSessionRepository cashRegisterSessionRepository;
    private final GetCurrentCashRegisterUseCase getCurrentCashRegisterUseCase;

    @Transactional(readOnly = true)
    public List<CashRegisterSessionDetail> execute(UUID branchId) {
        return cashRegisterSessionRepository.findByBranchId(branchId).stream()
            .map(getCurrentCashRegisterUseCase::toDetail)
            .sorted(Comparator.comparing(CashRegisterSessionDetail::openedAt).reversed())
            .toList();
    }
}
