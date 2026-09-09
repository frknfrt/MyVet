package com.vetos.modules.integration.efatura.domain;

/**
 * PROCESSING: saglayici (faturaentegrator) istegi kabul etti, GIB
 * resmilesmesi henuz tamamlanmadi -- callback (ApplyEInvoiceCallbackUseCase)
 * gercek ETTN ile SUBMITTED'e tasiyacak. Senkron/mock akiste bu ara durum
 * hic kullanilmaz, PENDING'den direkt SUBMITTED'e gecilir.
 */
public enum EInvoiceSubmissionStatus { PENDING, PROCESSING, SUBMITTED, FAILED }
