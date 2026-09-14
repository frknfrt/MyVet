package com.vetos.modules.integration.efatura.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * buyerIdentifier: Owner.nationalId artik toplanabiliyor olsa da (bkz.
 * docs/implementation-plan.md "TCKN karari" guncellemesi) e-Fatura akisi
 * hala GIB'in "isimsiz/nihai tuketici" icin ayirdigi ozel TCKN'sini
 * (11111111111) kullanir; her fatura otomatik e-Arsiv olarak kesilir.
 * Gercek TCKN'nin e-Fatura'ya baglanmasi ayrica degerlendirilecek bir is.
 * ownerId: FaturaEntegratorEInvoiceGatewayAdapter'da saglayici panelinde
 * ayni sahip icin ayni musteri kaydinin kullanilmasi (mukerrer musteri
 * olusmamasi) icin sabit bir sayisal ID turetmede kullanilir.
 */
public record EInvoiceSubmissionRequest(
    UUID invoiceId, UUID ownerId, EInvoiceDocumentType documentType,
    String buyerName, String buyerAddress, String buyerCity, String buyerDistrict, String buyerIdentifier,
    BigDecimal totalAmount, BigDecimal taxAmount, List<EInvoiceLineItem> lines, String callbackUrl
) {}
