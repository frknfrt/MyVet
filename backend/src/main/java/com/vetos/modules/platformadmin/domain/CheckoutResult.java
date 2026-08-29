package com.vetos.modules.platformadmin.domain;

/** iyzico'nun callback sonrasi "sonucu sorgula" cevabi. conversationId, initializeCheckout'a gecirilen invoice id'dir. */
public record CheckoutResult(boolean success, String conversationId, String paymentId) {}
