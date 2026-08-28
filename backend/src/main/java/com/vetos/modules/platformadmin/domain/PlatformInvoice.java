package com.vetos.modules.platformadmin.domain;

import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceInvalidTransitionException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "platform_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformInvoice {

    private static final int DUE_DAYS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformInvoiceStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    public static PlatformInvoice issue(
        UUID tenantId, String planCode, BigDecimal amount, LocalDate periodStart, LocalDate periodEnd, LocalDate issuedOn
    ) {
        PlatformInvoice invoice = new PlatformInvoice();
        invoice.tenantId = tenantId;
        invoice.planCode = planCode;
        invoice.amount = amount;
        invoice.periodStart = periodStart;
        invoice.periodEnd = periodEnd;
        invoice.dueDate = issuedOn.plusDays(DUE_DAYS);
        invoice.issuedAt = Instant.now();
        invoice.status = PlatformInvoiceStatus.ISSUED;
        return invoice;
    }

    public void markPaid() {
        if (status != PlatformInvoiceStatus.ISSUED && status != PlatformInvoiceStatus.OVERDUE) {
            throw new PlatformInvoiceInvalidTransitionException(status, PlatformInvoiceStatus.PAID);
        }
        this.status = PlatformInvoiceStatus.PAID;
        this.paidAt = Instant.now();
    }

    public void markOverdue() {
        if (status != PlatformInvoiceStatus.ISSUED) {
            throw new PlatformInvoiceInvalidTransitionException(status, PlatformInvoiceStatus.OVERDUE);
        }
        this.status = PlatformInvoiceStatus.OVERDUE;
    }

    public void voidInvoice() {
        if (status != PlatformInvoiceStatus.ISSUED && status != PlatformInvoiceStatus.OVERDUE) {
            throw new PlatformInvoiceInvalidTransitionException(status, PlatformInvoiceStatus.VOID);
        }
        this.status = PlatformInvoiceStatus.VOID;
    }
}
