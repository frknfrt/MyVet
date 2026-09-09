package com.vetos.modules.integration.efatura.domain;

import java.math.BigDecimal;

/**
 * Fatura satir kalemi -- billing modulundeki InvoiceLine'dan turetilir
 * (bkz. EInvoiceSubmissionExecutor). unit alani UN/UBL olcum kodu (varsayilan
 * "C62" = adet, InvoiceLine'da ayrica bir olcu birimi alani yok).
 */
public record EInvoiceLineItem(
    String externalId, String name, int quantity, String unit, BigDecimal unitPrice, BigDecimal vatRatePercent
) {}
