package com.vetos.modules.billing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;

    @Column(name = "service_type_id")
    private UUID serviceTypeId;

    @Column(name = "inventory_item_id")
    private UUID inventoryItemId;

    @Column(name = "discount_amount", nullable = false)
    private BigDecimal discountAmount;

    @Column(name = "vat_rate", nullable = false)
    private BigDecimal vatRate;

    @Column(name = "vat_amount", nullable = false)
    private BigDecimal vatAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceLineSource source;

    public static InvoiceLine create(
        UUID tenantId, UUID invoiceId, String description, int quantity, BigDecimal unitPrice,
        UUID serviceTypeId, UUID inventoryItemId, InvoiceLineSource source
    ) {
        return create(tenantId, invoiceId, description, quantity, unitPrice, BigDecimal.ZERO, BigDecimal.ZERO, serviceTypeId, inventoryItemId, source);
    }

    public static InvoiceLine create(
        UUID tenantId, UUID invoiceId, String description, int quantity, BigDecimal unitPrice,
        BigDecimal discountAmount, BigDecimal vatRate,
        UUID serviceTypeId, UUID inventoryItemId, InvoiceLineSource source
    ) {
        InvoiceLine line = new InvoiceLine();
        line.tenantId = tenantId;
        line.invoiceId = invoiceId;
        line.description = description;
        line.quantity = quantity;
        line.unitPrice = unitPrice;
        line.discountAmount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        line.vatRate = vatRate == null ? BigDecimal.ZERO : vatRate;

        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).subtract(line.discountAmount);
        line.vatAmount = subtotal.multiply(line.vatRate).divide(BigDecimal.valueOf(100));
        line.lineTotal = subtotal.add(line.vatAmount);

        line.serviceTypeId = serviceTypeId;
        line.inventoryItemId = inventoryItemId;
        line.source = source;
        return line;
    }
}
