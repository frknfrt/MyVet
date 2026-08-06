package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.InvoiceDetail;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.exception.InvoiceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetInvoiceByBoardingStayUseCase {

    private final InvoiceRepository invoiceRepository;
    private final GetInvoiceUseCase getInvoiceUseCase;

    @Transactional(readOnly = true)
    public InvoiceDetail execute(UUID boardingStayId) {
        var invoice = invoiceRepository.findByBoardingStayId(boardingStayId)
            .orElseThrow(() -> new InvoiceNotFoundException(boardingStayId));
        return getInvoiceUseCase.execute(invoice.getId());
    }
}
