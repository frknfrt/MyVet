package com.vetos.modules.platformadmin.domain;

/** iyzico Checkout Form baslatma sonucu -- checkoutFormUrl'e tarayici tam sayfa yonlendirilir. */
public record CheckoutSession(String checkoutFormUrl, String token) {}
