package com.vetos.modules.billing.infrastructure.persistence;

import com.vetos.modules.billing.domain.Payment;
import com.vetos.modules.billing.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    @Override
    public Payment save(Payment payment) { return jpaRepository.save(payment); }

    @Override
    public List<Payment> findByInvoiceId(UUID invoiceId) { return jpaRepository.findByInvoiceId(invoiceId); }
}
