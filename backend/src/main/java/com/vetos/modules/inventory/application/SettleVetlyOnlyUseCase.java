package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.DeductionSettlement;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.event.PendingDeductionSettledVetlyOnlyEvent;
import com.vetos.platform.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** "Yalniz Vetly'den dus" (spec 2026-10-09 S5.1): bekleyenler planlanan adetle duser, TARBIL aktarimi reddedilir (olayla). */
@Service
@RequiredArgsConstructor
public class SettleVetlyOnlyUseCase {

    private final PendingDeductionService service;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void execute(StockReferenceType type, UUID referenceId, UUID staffId) {
        if (service.settle(type, referenceId, null, DeductionSettlement.VETLY_ONLY, staffId).settledCount() > 0) {
            eventPublisher.publish(new PendingDeductionSettledVetlyOnlyEvent(type, referenceId, staffId));
        }
    }
}
