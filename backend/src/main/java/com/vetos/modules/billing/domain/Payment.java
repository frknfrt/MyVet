package com.vetos.modules.billing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "psp_ref")
    private String pspRef;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    public static Payment record(UUID invoiceId, PaymentMethod method, BigDecimal amount, String pspRef) {
        Payment payment = new Payment();
        payment.invoiceId = invoiceId;
        payment.method = method;
        payment.amount = amount;
        payment.pspRef = pspRef;
        payment.paidAt = Instant.now();
        return payment;
    }
}
