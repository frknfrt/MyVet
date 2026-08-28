package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.PlatformPayment;
import com.vetos.modules.platformadmin.domain.PlatformPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PlatformPaymentRepositoryAdapter implements PlatformPaymentRepository {

    private final PlatformPaymentJpaRepository jpaRepository;

    @Override
    public PlatformPayment save(PlatformPayment payment) { return jpaRepository.save(payment); }

    @Override
    public Optional<PlatformPayment> findByInvoiceId(UUID invoiceId) { return jpaRepository.findByInvoiceId(invoiceId); }
}
