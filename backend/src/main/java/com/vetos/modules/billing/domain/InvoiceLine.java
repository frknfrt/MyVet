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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceLineSource source;

    public static InvoiceLine create(
        UUID invoiceId, String description, int quantity, BigDecimal unitPrice,
        UUID serviceTypeId, UUID inventoryItemId, InvoiceLineSource source
    ) {
        InvoiceLine line = new InvoiceLine();
        line.invoiceId = invoiceId;
        line.description = description;
        line.quantity = quantity;
        line.unitPrice = unitPrice;
        line.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        line.serviceTypeId = serviceTypeId;
        line.inventoryItemId = inventoryItemId;
        line.source = source;
        return line;
    }
}
