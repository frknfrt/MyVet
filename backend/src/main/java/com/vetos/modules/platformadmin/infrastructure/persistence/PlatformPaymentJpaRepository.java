package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.PlatformPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PlatformPaymentJpaRepository extends JpaRepository<PlatformPayment, UUID> {
    Optional<PlatformPayment> findByInvoiceId(UUID invoiceId);
}
