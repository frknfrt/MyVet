package com.vetos.modules.billing.infrastructure.event;

import com.vetos.modules.billing.application.CreateBoardingStayInvoiceUseCase;
import com.vetos.modules.boarding.domain.event.BoardingStayCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component("billingBoardingStayCreatedEventListener")
@RequiredArgsConstructor
class BoardingStayCreatedEventListener {

    private final CreateBoardingStayInvoiceUseCase createBoardingStayInvoiceUseCase;

    @EventListener
    void onBoardingStayCreated(BoardingStayCreatedEvent event) {
        createBoardingStayInvoiceUseCase.execute(
            event.tenantId(), event.branchId(), event.ownerId(), event.boardingStayId(),
            event.roomLabel(), event.dailyRate(), event.checkInDate(), event.expectedCheckOutDate()
        );
    }
}
