package com.vetos.modules.billing.domain;

import com.vetos.modules.billing.domain.exception.InvoiceInvalidStateException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "boarding_stay_id")
    private UUID boardingStayId;

    @Column(name = "staff_user_id")
    private UUID staffUserId;

    @Column(name = "e_invoice_ref")
    private String eInvoiceRef;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "tax_amount", nullable = false)
    private BigDecimal taxAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Column(name = "issued_at")
    private Instant issuedAt;

    public static Invoice createDraft(UUID tenantId, UUID branchId, UUID ownerId, UUID encounterId, UUID staffUserId) {
        Invoice invoice = new Invoice();
        invoice.tenantId = tenantId;
        invoice.branchId = branchId;
        invoice.ownerId = ownerId;
        invoice.encounterId = encounterId;
        invoice.staffUserId = staffUserId;
        invoice.totalAmount = BigDecimal.ZERO;
        invoice.taxAmount = BigDecimal.ZERO;
        invoice.status = InvoiceStatus.DRAFT;
        return invoice;
    }

    public static Invoice createDraftForBoardingStay(UUID tenantId, UUID branchId, UUID ownerId, UUID boardingStayId) {
        Invoice invoice = createDraft(tenantId, branchId, ownerId, null, null);
        invoice.boardingStayId = boardingStayId;
        return invoice;
    }

    public void recalculateTotal(BigDecimal newTotal) {
        this.totalAmount = newTotal;
    }

    public void recalculateTax(BigDecimal newTax) {
        this.taxAmount = newTax;
    }

    public void applyEInvoiceReference(String eInvoiceRef) {
        this.eInvoiceRef = eInvoiceRef;
    }

    public void issue() {
        if (status != InvoiceStatus.DRAFT) {
            throw new InvoiceInvalidStateException(id, "Sadece DRAFT fatura kesilebilir, mevcut durum: " + status);
        }
        this.status = InvoiceStatus.ISSUED;
        this.issuedAt = Instant.now();
    }

    public void applyPaymentStatus(BigDecimal totalPaid) {
        if (status == InvoiceStatus.VOID || status == InvoiceStatus.DRAFT) {
            throw new InvoiceInvalidStateException(id, "Odeme sadece kesilmis (ISSUED) faturaya islenebilir");
        }
        if (totalPaid.compareTo(totalAmount) >= 0) {
            this.status = InvoiceStatus.PAID;
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            this.status = InvoiceStatus.PARTIALLY_PAID;
        }
    }

    public void voidInvoice() {
        if (status == InvoiceStatus.PAID) {
            throw new InvoiceInvalidStateException(id, "Odenmis fatura iptal edilemez");
        }
        this.status = InvoiceStatus.VOID;
    }
}
