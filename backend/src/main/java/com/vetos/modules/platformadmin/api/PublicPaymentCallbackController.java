package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.application.HandlePaymentCallbackUseCase;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;

/**
 * iyzico'nun Checkout Form callback'i icin kimlik dogrulama gerektirmeyen uc
 * nokta -- SecurityConfig'de /api/v1/public/** zaten permitAll (PublicClinicController
 * ile ayni desen). POST: gercek iyzico callback'i (form param 'token'). GET:
 * sadece IyzicoPaymentGatewayAdapter simule modundayken kullanilir -- gelistirici
 * "Ode" butonuna basinca gercek bir iyzico sayfasi olmadan bu uc noktaya
 * dogrudan yonlendirilir. Gateway gercekten yapilandirilmissa (isConfigured())
 * GET tamamen reddedilir; gercek iyzico her zaman POST eder.
 */
@RestController
@RequestMapping("/api/v1/public/payments/iyzico/callback")
@Slf4j
public class PublicPaymentCallbackController {

    private final HandlePaymentCallbackUseCase handlePaymentCallbackUseCase;
    private final PaymentGatewayPort paymentGatewayPort;
    private final String frontendBaseUrl;

    PublicPaymentCallbackController(
        HandlePaymentCallbackUseCase handlePaymentCallbackUseCase,
        PaymentGatewayPort paymentGatewayPort,
        @Value("${app.frontend-base-url}") String frontendBaseUrl
    ) {
        this.handlePaymentCallbackUseCase = handlePaymentCallbackUseCase;
        this.paymentGatewayPort = paymentGatewayPort;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @PostMapping
    public ResponseEntity<Void> handlePost(@RequestParam String token) {
        return handleAndRedirect(token);
    }

    @GetMapping
    public ResponseEntity<Void> handleGet(@RequestParam String token) {
        // Derinlemesine savunma: GET rotasi yalnizca yerel simule mod icin var.
        // Gateway yapilandirilmissa token'a hic dokunmadan hata sayfasina donulur.
        if (paymentGatewayPort.isConfigured()) {
            log.warn("iyzico callback GET reddedildi -- gateway yapilandirilmis, sadece POST kabul edilir");
            return redirect(false);
        }
        return handleAndRedirect(token);
    }

    private ResponseEntity<Void> handleAndRedirect(String token) {
        boolean success;
        try {
            success = handlePaymentCallbackUseCase.execute(token, LocalDate.now());
        } catch (Exception e) {
            // Kullanicinin karti cekildikten hemen sonra tarayicida ham JSON hata
            // govdesi gostermek yerine her durumda frontend'e geri yonlendir.
            log.error("iyzico callback islenemedi: token={}", token, e);
            success = false;
        }
        return redirect(success);
    }

    private ResponseEntity<Void> redirect(boolean success) {
        String redirectUrl = frontendBaseUrl + "/ayarlar/abonelik?odeme=" + (success ? "basarili" : "hata");
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }
}
