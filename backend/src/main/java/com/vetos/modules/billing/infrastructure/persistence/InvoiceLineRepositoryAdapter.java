package com.vetos.modules.billing.infrastructure.persistence;

import com.vetos.modules.billing.domain.InvoiceLine;
import com.vetos.modules.billing.domain.InvoiceLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class InvoiceLineRepositoryAdapter implements InvoiceLineRepository {

    private final InvoiceLineJpaRepository jpaRepository;

    @Override
    public InvoiceLine save(InvoiceLine line) { return jpaRepository.save(line); }

    @Override
    public List<InvoiceLine> findByInvoiceId(UUID invoiceId) { return jpaRepository.findByInvoiceId(invoiceId); }
}
