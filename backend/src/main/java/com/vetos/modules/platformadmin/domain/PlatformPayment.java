package com.vetos.modules.platformadmin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "platform_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformPaymentMethod method;

    @Column(name = "paid_at", nullable = false)
    private LocalDate paidAt;

    @Column(name = "recorded_by_admin_id", nullable = false)
    private UUID recordedByAdminId;

    @Column(columnDefinition = "text")
    private String notes;

    public static PlatformPayment record(
        UUID invoiceId, BigDecimal amount, PlatformPaymentMethod method, LocalDate paidAt, UUID recordedByAdminId, String notes
    ) {
        PlatformPayment payment = new PlatformPayment();
        payment.invoiceId = invoiceId;
        payment.amount = amount;
        payment.method = method;
        payment.paidAt = paidAt;
        payment.recordedByAdminId = recordedByAdminId;
        payment.notes = notes;
        return payment;
    }
}
