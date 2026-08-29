package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.application.HandlePaymentCallbackUseCase;
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
 * dogrudan yonlendirilir.
 */
@RestController
@RequestMapping("/api/v1/public/payments/iyzico/callback")
public class PublicPaymentCallbackController {

    private final HandlePaymentCallbackUseCase handlePaymentCallbackUseCase;
    private final String frontendBaseUrl;

    PublicPaymentCallbackController(
        HandlePaymentCallbackUseCase handlePaymentCallbackUseCase,
        @Value("${app.frontend-base-url}") String frontendBaseUrl
    ) {
        this.handlePaymentCallbackUseCase = handlePaymentCallbackUseCase;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @PostMapping
    public ResponseEntity<Void> handlePost(@RequestParam String token) {
        return handleAndRedirect(token);
    }

    @GetMapping
    public ResponseEntity<Void> handleGet(@RequestParam String token) {
        return handleAndRedirect(token);
    }

    private ResponseEntity<Void> handleAndRedirect(String token) {
        boolean success = handlePaymentCallbackUseCase.execute(token, LocalDate.now());
        String redirectUrl = frontendBaseUrl + "/ayarlar/abonelik?odeme=" + (success ? "basarili" : "hata");
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }
}
