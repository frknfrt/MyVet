package com.vetos.modules.billing.domain;

import java.math.BigDecimal;

/**
 * Bir fatura kumesinin toplam tutari ve adedi -- veritabaninda toplanip
 * donen kucuk sonuc tipi. Faturalarin tamamini belleğe cekmeden KPI
 * hesaplamak icin kullanilir (bkz. InvoiceRepository#sumIssuedBetween).
 */
public record SalesAggregate(BigDecimal totalAmount, long count) {

    /** SUM(), hic satir eslesmediginde null doner; cagiran taraf null gormesin. */
    public SalesAggregate {
        totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
    }
}
