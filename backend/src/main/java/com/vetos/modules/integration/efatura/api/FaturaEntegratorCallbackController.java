package com.vetos.modules.integration.efatura.api;

import com.vetos.modules.integration.efatura.api.dto.FaturaEntegratorCallbackRequest;
import com.vetos.modules.integration.efatura.application.ApplyEInvoiceCallbackUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * faturaentegrator.com'un callback_url'ine POST ettigi asenkron bildirimi
 * karsilar -- kimlik dogrulama gerektirmeyen uc nokta (SecurityConfig'de
 * /api/v1/public/** zaten permitAll, bkz. PublicPaymentCallbackController ile
 * ayni desen). Bunun yerine HMAC-SHA512 IMZA dogrulamasiyla gercekten
 * faturaentegrator'dan geldigi teyit edilir (bkz. dokumantasyon "Imza
 * dogrulama (hash)": hash = HMAC-SHA512(invoice_id + team_id + time, API_ANAHTARI)).
 *
 * Govde durum bilgisi tasimaz -- sadece "bir sey degisti" bildirimidir; guncel
 * durum ApplyEInvoiceCallbackUseCase icinde ayrica GET /invoices/{id} ile
 * sorgulanir. Dokumantasyon "hemen 200 dönün" diyor -- bu tek ek GET cagrisi
 * 10 saniyelik zaman asimi icinde rahatlikla tamamlanir, ayri bir kuyruga
 * almaya gerek yok.
 */
@RestController
@RequestMapping("/api/v1/public/efatura/faturaentegrator/callback")
@Slf4j
public class FaturaEntegratorCallbackController {

    private final ApplyEInvoiceCallbackUseCase applyEInvoiceCallbackUseCase;
    private final String apiKey;

    FaturaEntegratorCallbackController(
        ApplyEInvoiceCallbackUseCase applyEInvoiceCallbackUseCase,
        @Value("${efatura.faturaentegrator.api-key:}") String apiKey
    ) {
        this.applyEInvoiceCallbackUseCase = applyEInvoiceCallbackUseCase;
        this.apiKey = apiKey;
    }

    @PostMapping
    public ResponseEntity<Void> handleCallback(@RequestBody FaturaEntegratorCallbackRequest request) {
        if (!isSignatureValid(request)) {
            log.warn("faturaentegrator callback imzasi gecersiz: invoiceId={}", request.invoiceId());
            return ResponseEntity.status(401).build();
        }

        try {
            applyEInvoiceCallbackUseCase.execute(String.valueOf(request.invoiceId()));
        } catch (Exception e) {
            // Dokumantasyon 2xx bekliyor ki saglayici tekrar denemesin -- ama islenemeyen
            // bir bildirim submission'i PROCESSING'de birakir, bir sonraki callback veya
            // manuel "Tekrar Dene" (PROCESSING guard'i asilamadigi icin aslinda burada
            // manuel mudahale gerekir) ile telafi edilir. Sessizce yutmak yerine loglanir.
            log.error("faturaentegrator callback islenemedi: invoiceId={}", request.invoiceId(), e);
        }
        return ResponseEntity.ok().build();
    }

    private boolean isSignatureValid(FaturaEntegratorCallbackRequest request) {
        if (apiKey.isBlank() || request.hash() == null || request.time() == null) {
            return false;
        }
        String data = request.invoiceId() + String.valueOf(request.teamId()) + request.time();
        String expected = hmacSha512Hex(data, apiKey);
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), request.hash().getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String hmacSha512Hex(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA512 kullanilamiyor", e);
        }
    }
}
