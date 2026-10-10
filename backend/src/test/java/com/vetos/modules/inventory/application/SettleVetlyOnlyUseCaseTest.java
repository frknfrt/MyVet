package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.DeductionSettlement;
import com.vetos.modules.inventory.domain.DeductionSettlementResult;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.event.PendingDeductionSettledVetlyOnlyEvent;
import com.vetos.platform.event.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettleVetlyOnlyUseCaseTest {

    @Mock private PendingDeductionService service;
    @Mock private DomainEventPublisher publisher;

    @Test
    void settlesPendingLinesAndAnnouncesIt() {
        UUID ref = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        when(service.settle(StockReferenceType.ENCOUNTER, ref, null, DeductionSettlement.VETLY_ONLY, staff))
            .thenReturn(new DeductionSettlementResult(2, List.of()));

        new SettleVetlyOnlyUseCase(service, publisher).execute(StockReferenceType.ENCOUNTER, ref, staff);

        verify(publisher).publish(new PendingDeductionSettledVetlyOnlyEvent(StockReferenceType.ENCOUNTER, ref, staff));
    }

    @Test
    void doesNotAnnounceWhenNothingWasPending() {
        UUID ref = UUID.randomUUID();
        when(service.settle(any(), any(), any(), any(), any())).thenReturn(new DeductionSettlementResult(0, List.of()));

        new SettleVetlyOnlyUseCase(service, publisher).execute(StockReferenceType.ENCOUNTER, ref, UUID.randomUUID());

        verifyNoInteractions(publisher);
    }
}
