package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.application.HandlePaymentCallbackUseCase;
import com.vetos.modules.platformadmin.application.HandleSignupPaymentCallbackUseCase;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
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
 *
 * Iki farkli odeme senaryosunu ayni callback'te ele alir: (1) mevcut bir
 * tenant'in kendi PlatformInvoice'unu odemesi (HandlePaymentCallbackUseCase),
 * (2) vetly.com'da yeni bir kayit odemesi (HandleSignupPaymentCallbackUseCase).
 * conversationId hangi turden oldugunu kendi basina soylemedigi icin once
 * fatura-odemesi olarak denenir; PlatformInvoiceNotFoundException gelirse
 * kayit-odemesi olarak denenir. Basarili sonucta HANGI akisin isledigine
 * gore FARKLI bir siteye (ana uygulama ya da vetly-site) yonlendirilir.
 */
@RestController
@RequestMapping("/api/v1/public/payments/iyzico/callback")
@Slf4j
public class PublicPaymentCallbackController {

    private final HandlePaymentCallbackUseCase handlePaymentCallbackUseCase;
    private final HandleSignupPaymentCallbackUseCase handleSignupPaymentCallbackUseCase;
    private final PaymentGatewayPort paymentGatewayPort;
    private final String frontendBaseUrl;
    private final String vetlySiteOrigin;

    PublicPaymentCallbackController(
        HandlePaymentCallbackUseCase handlePaymentCallbackUseCase,
        HandleSignupPaymentCallbackUseCase handleSignupPaymentCallbackUseCase,
        PaymentGatewayPort paymentGatewayPort,
        @Value("${app.frontend-base-url}") String frontendBaseUrl,
        @Value("${app.vetly-site-origin:http://localhost:5175}") String vetlySiteOrigin
    ) {
        this.handlePaymentCallbackUseCase = handlePaymentCallbackUseCase;
        this.handleSignupPaymentCallbackUseCase = handleSignupPaymentCallbackUseCase;
        this.paymentGatewayPort = paymentGatewayPort;
        this.frontendBaseUrl = frontendBaseUrl;
        this.vetlySiteOrigin = vetlySiteOrigin;
    }

    @PostMapping
    public ResponseEntity<Void> handlePost(@RequestParam String token) {
        return handleAndRedirect(token);
    }

    @GetMapping
    public ResponseEntity<Void> handleGet(@RequestParam String token) {
        if (paymentGatewayPort.isConfigured()) {
            log.warn("iyzico callback GET reddedildi -- gateway yapilandirilmis, sadece POST kabul edilir");
            return redirectToApp(false);
        }
        return handleAndRedirect(token);
    }

    private ResponseEntity<Void> handleAndRedirect(String token) {
        LocalDate today = LocalDate.now();
        try {
            boolean success = handlePaymentCallbackUseCase.execute(token, today);
            return redirectToApp(success);
        } catch (PlatformInvoiceNotFoundException notAnInvoicePayment) {
            try {
                boolean success = handleSignupPaymentCallbackUseCase.execute(token, today);
                return redirectToSignup(success);
            } catch (Exception e) {
                log.error("iyzico kayit callback'i islenemedi: token={}", token, e);
                return redirectToSignup(false);
            }
        } catch (Exception e) {
            log.error("iyzico callback islenemedi: token={}", token, e);
            return redirectToApp(false);
        }
    }

    private ResponseEntity<Void> redirectToApp(boolean success) {
        String redirectUrl = frontendBaseUrl + "/ayarlar/abonelik?odeme=" + (success ? "basarili" : "hata");
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    private ResponseEntity<Void> redirectToSignup(boolean success) {
        String redirectUrl = vetlySiteOrigin + "/?kayit=" + (success ? "basarili" : "hata");
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }
}
