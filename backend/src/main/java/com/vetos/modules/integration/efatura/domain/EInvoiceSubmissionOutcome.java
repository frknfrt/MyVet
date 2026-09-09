package com.vetos.modules.integration.efatura.domain;

/**
 * finalResult=true oldugunda gibReference NIHAI GIB referansidir (senkron/mock
 * akis -- MockEInvoiceGatewayAdapter). finalResult=false oldugunda gibReference
 * saglayicinin KENDI takip numarasidir (provider_reference); GIB resmilesmesi
 * henuz tamamlanmadi, nihai ETTN daha sonra callback ile gelir (bkz.
 * FaturaEntegratorEInvoiceGatewayAdapter, ApplyEInvoiceCallbackUseCase).
 */
public record EInvoiceSubmissionOutcome(boolean success, boolean finalResult, String gibReference, String message) {

    public static EInvoiceSubmissionOutcome success(String gibReference, String message) {
        return new EInvoiceSubmissionOutcome(true, true, gibReference, message);
    }

    public static EInvoiceSubmissionOutcome pending(String providerReference, String message) {
        return new EInvoiceSubmissionOutcome(true, false, providerReference, message);
    }

    public static EInvoiceSubmissionOutcome failure(String message) {
        return new EInvoiceSubmissionOutcome(false, false, null, message);
    }
}
