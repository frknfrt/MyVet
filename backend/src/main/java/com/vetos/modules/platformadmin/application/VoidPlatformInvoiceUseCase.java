package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoidPlatformInvoiceUseCase {

    private final PlatformInvoiceRepository platformInvoiceRepository;

    @Transactional
    public void execute(UUID invoiceId) {
        PlatformInvoice invoice = platformInvoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new PlatformInvoiceNotFoundException(invoiceId));
        invoice.voidInvoice();
        platformInvoiceRepository.save(invoice);
    }
}
