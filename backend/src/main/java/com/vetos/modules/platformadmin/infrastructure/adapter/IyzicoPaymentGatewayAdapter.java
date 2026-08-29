package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.CheckoutResult;
import com.vetos.modules.platformadmin.domain.CheckoutSession;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.exception.PaymentGatewayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * iyzico Checkout Form (hosted odeme sayfasi) entegrasyonu. Gercek bir iyzico
 * sandbox hesabi bu ortamda henuz yok -- IYZICO_API_KEY/IYZICO_SECRET_KEY bos
 * ise tum cagrilar simule edilir (TwilioNotificationAdapter'daki ayni desen,
 * bkz. isConfigured()). GERCEK MOD HTTP cagrilari iyzico'nun genel REST API
 * dokumantasyonuna (Checkout Form v2, IYZWSv2 HMAC-SHA256 imzalama) gore
 * yazildi ama canli bir sandbox'a karsi hic test edilmedi -- gercek
 * credential eklendiginde bir sandbox islemiyle mutlaka dogrulanmali.
 */
@Component
@Slf4j
class IyzicoPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final String INITIALIZE_PATH = "/payment/iyzipos/checkoutform/initialize/auth/ecom";
    private static final String RETRIEVE_PATH = "/payment/iyzipos/checkoutform/auth/ecom/detail";
    private static final String SIMULATED_TOKEN_PREFIX = "SIMULATED-";

    private final String apiKey;
    private final String secretKey;
    private final String baseUrl;
    private final String callbackBaseUrl;
    private final RestClient restClient;

    IyzicoPaymentGatewayAdapter(
        @Value("${payment-gateway.iyzico.api-key:}") String apiKey,
        @Value("${payment-gateway.iyzico.secret-key:}") String secretKey,
        @Value("${payment-gateway.iyzico.base-url:https://sandbox-api.iyzipay.com}") String baseUrl,
        @Value("${payment-gateway.iyzico.callback-base-url:http://localhost:8080}") String callbackBaseUrl
    ) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.baseUrl = baseUrl;
        this.callbackBaseUrl = callbackBaseUrl;
        this.restClient = RestClient.create();
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank() && !secretKey.isBlank();
    }

    @Override
    public CheckoutSession initializeCheckout(String conversationId, BigDecimal amount, String buyerName, String buyerEmail) {
        if (!isConfigured()) {
            String simulatedToken = SIMULATED_TOKEN_PREFIX + conversationId;
            String checkoutFormUrl = callbackBaseUrl + "/api/v1/public/payments/iyzico/callback?token=" + simulatedToken;
            log.info("iyzico checkout (simule): conversationId={}, tutar={}", conversationId, amount);
            return new CheckoutSession(checkoutFormUrl, simulatedToken);
        }

        String requestBody = """
            {
              "locale": "tr",
              "conversationId": "%s",
              "price": "%s",
              "paidPrice": "%s",
              "currency": "TRY",
              "basketId": "%s",
              "paymentGroup": "SUBSCRIPTION",
              "callbackUrl": "%s/api/v1/public/payments/iyzico/callback",
              "enabledInstallments": [1],
              "buyer": {"id": "%s", "name": "%s", "surname": "-", "gsmNumber": "+905000000000", "email": "%s", "identityNumber": "11111111111", "registrationAddress": "-", "ip": "127.0.0.1", "city": "Istanbul", "country": "Turkey", "zipCode": "34000"},
              "shippingAddress": {"contactName": "%s", "city": "Istanbul", "country": "Turkey", "address": "-", "zipCode": "34000"},
              "billingAddress": {"contactName": "%s", "city": "Istanbul", "country": "Turkey", "address": "-", "zipCode": "34000"},
              "basketItems": [{"id": "%s", "name": "Vetly Abonelik", "category1": "SaaS", "itemType": "VIRTUAL", "price": "%s"}]
            }
            """.formatted(
            conversationId, amount, amount, conversationId, callbackBaseUrl, conversationId,
            buyerName, buyerEmail, buyerName, buyerName, conversationId, amount
        );

        try {
            Map<String, Object> response = restClient.post()
                .uri(baseUrl + INITIALIZE_PATH)
                .header("Authorization", authorizationHeader(INITIALIZE_PATH, requestBody))
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

            return new CheckoutSession((String) response.get("paymentPageUrl"), (String) response.get("token"));
        } catch (RestClientResponseException e) {
            log.warn("iyzico checkout baslatilamadi: durum={}, govde={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentGatewayException("iyzico checkout baslatilamadi", e);
        }
    }

    @Override
    public CheckoutResult retrieveCheckoutResult(String token) {
        if (token.startsWith(SIMULATED_TOKEN_PREFIX)) {
            String conversationId = token.substring(SIMULATED_TOKEN_PREFIX.length());
            log.info("iyzico odeme sonucu (simule): conversationId={}", conversationId);
            return new CheckoutResult(true, conversationId, "SIMULATED-PAYMENT-" + UUID.randomUUID());
        }

        String requestBody = "{\"locale\": \"tr\", \"token\": \"%s\"}".formatted(token);
        try {
            Map<String, Object> response = restClient.post()
                .uri(baseUrl + RETRIEVE_PATH)
                .header("Authorization", authorizationHeader(RETRIEVE_PATH, requestBody))
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

            boolean success = "success".equals(response.get("status")) && "SUCCESS".equals(response.get("paymentStatus"));
            return new CheckoutResult(success, (String) response.get("conversationId"), (String) response.get("paymentId"));
        } catch (RestClientResponseException e) {
            log.warn("iyzico odeme sonucu sorgulanamadi: durum={}, govde={}", e.getStatusCode(), e.getResponseBodyAsString());
            return new CheckoutResult(false, null, null);
        }
    }

    private String authorizationHeader(String uriPath, String requestBody) {
        String randomKey = System.currentTimeMillis() + UUID.randomUUID().toString();
        String signature = hmacSha256Hex(randomKey + uriPath + requestBody, secretKey);
        String authorizationParams = "apiKey:" + apiKey + "&randomKey:" + randomKey + "&signature:" + signature;
        String encoded = Base64.getEncoder().encodeToString(authorizationParams.getBytes(StandardCharsets.UTF_8));
        return "IYZWSv2 " + encoded;
    }

    private static String hmacSha256Hex(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 kullanilamiyor", e);
        }
    }
}
