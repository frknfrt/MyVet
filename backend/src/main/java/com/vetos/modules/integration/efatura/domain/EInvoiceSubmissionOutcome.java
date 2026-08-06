package com.vetos.modules.integration.efatura.domain;

public record EInvoiceSubmissionOutcome(boolean success, String gibReference, String message) {

    public static EInvoiceSubmissionOutcome success(String gibReference, String message) {
        return new EInvoiceSubmissionOutcome(true, gibReference, message);
    }

    public static EInvoiceSubmissionOutcome failure(String message) {
        return new EInvoiceSubmissionOutcome(false, null, message);
    }
}
