package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.application.PendingDeductionService;
import com.vetos.modules.inventory.domain.DeductionSettlement;
import com.vetos.modules.inventory.domain.DeductionSettlementResult;
import com.vetos.modules.inventory.domain.PendingDeductionLine;
import com.vetos.modules.inventory.domain.ReportedDeductionLine;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.TarbilStockDeductionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilStockDeductionAdapter implements TarbilStockDeductionPort {

    private final PendingDeductionService service;

    @Override
    public List<PendingDeductionLine> listForReference(StockReferenceType type, UUID referenceId) {
        return service.lines(type, referenceId);
    }

    @Override
    public DeductionSettlementResult settleFromTarbil(StockReferenceType type, UUID referenceId, List<ReportedDeductionLine> reported, UUID staffId) {
        return service.settle(type, referenceId, reported, DeductionSettlement.TARBIL, staffId);
    }
}
