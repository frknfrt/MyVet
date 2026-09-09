package com.vetos.modules.integration.efatura.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * buyerIdentifier: Faz 1'de sahiplerin TCKN'si sistemde hic tutulmuyor
 * (sadece nationalIdMasked var) -- bu yuzden GIB'in "isimsiz/nihai tuketici"
 * icin ayirdigi ozel TCKN'si (11111111111) kullanilir; her fatura otomatik
 * e-Arsiv olarak kesilir (bkz. docs/implementation-plan.md e-Fatura bolumu).
 * ownerId: FaturaEntegratorEInvoiceGatewayAdapter'da saglayici panelinde
 * ayni sahip icin ayni musteri kaydinin kullanilmasi (mukerrer musteri
 * olusmamasi) icin sabit bir sayisal ID turetmede kullanilir.
 */
public record EInvoiceSubmissionRequest(
    UUID invoiceId, UUID ownerId, EInvoiceDocumentType documentType,
    String buyerName, String buyerAddress, String buyerCity, String buyerDistrict, String buyerIdentifier,
    BigDecimal totalAmount, BigDecimal taxAmount, List<EInvoiceLineItem> lines, String callbackUrl
) {}
