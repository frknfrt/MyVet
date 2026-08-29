package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
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
 *
 * GUVENLIK: simulasyon kisayollari SADECE !isConfigured() iken devreye girer.
 * Kimlik bilgileri tanimliyken "SIMULATED-" onekli bir token da gercek mod
 * HTTP cagrisina duser (ve iyzico tarafinda gecersiz token olarak reddedilir);
 * boylece public callback ucundan bedava "odendi" isaretleme mumkun degildir.
 */
@Component
@Slf4j
class IyzicoPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final String INITIALIZE_PATH = "/payment/iyzipos/checkoutform/initialize/auth/ecom";
    private static final String RETRIEVE_PATH = "/payment/iyzipos/checkoutform/auth/ecom/detail";
    private static final String SIMULATED_TOKEN_PREFIX = "SIMULATED-";
    private static final String CALLBACK_PATH = "/api/v1/public/payments/iyzico/callback";

    private final String apiKey;
    private final String secretKey;
    private final String baseUrl;
    private final String callbackBaseUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            String checkoutFormUrl = callbackBaseUrl + CALLBACK_PATH + "?token=" + simulatedToken;
            log.info("iyzico checkout (simule): conversationId={}, tutar={}", conversationId, amount);
            return new CheckoutSession(checkoutFormUrl, simulatedToken);
        }

        String requestBody = toJson(
            initializeRequestBody(conversationId, amount, buyerName, buyerEmail, callbackBaseUrl + CALLBACK_PATH)
        );

        Map<String, Object> response;
        try {
            response = postSigned(INITIALIZE_PATH, requestBody);
        } catch (RestClientResponseException e) {
            log.warn("iyzico checkout baslatilamadi: durum={}, govde={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentGatewayException("iyzico checkout baslatilamadi", e);
        }

        // iyzico is-seviyesi hatalari HTTP 200 + {"status":"failure",...} olarak
        // doner; status kontrolu olmadan CheckoutSession(null, null) uretilir ve
        // frontend "null" adresine gider.
        if (response == null || !"success".equals(response.get("status"))) {
            log.warn("iyzico checkout basarisiz yanit dondu: govde={}", response);
            throw new PaymentGatewayException("iyzico checkout basarisiz: " + response, null);
        }

        return new CheckoutSession((String) response.get("paymentPageUrl"), (String) response.get("token"));
    }

    @Override
    public CheckoutResult retrieveCheckoutResult(String token) {
        // Simulasyon kisayolu SADECE kimlik bilgisi tanimli degilken gecerlidir --
        // aksi halde public callback ucuna "SIMULATED-<fatura-id>" gonderen herkes
        // ilgili faturayi bedava odenmis isaretletebilirdi.
        if (!isConfigured() && token.startsWith(SIMULATED_TOKEN_PREFIX)) {
            String conversationId = token.substring(SIMULATED_TOKEN_PREFIX.length());
            log.info("iyzico odeme sonucu (simule): conversationId={}", conversationId);
            return new CheckoutResult(true, conversationId, "SIMULATED-PAYMENT-" + UUID.randomUUID());
        }

        Map<String, Object> retrieveBody = new LinkedHashMap<>();
        retrieveBody.put("locale", "tr");
        retrieveBody.put("token", token);
        String requestBody = toJson(retrieveBody);

        try {
            Map<String, Object> response = postSigned(RETRIEVE_PATH, requestBody);
            if (response == null) {
                log.warn("iyzico odeme sonucu bos govde dondu");
                return new CheckoutResult(false, null, null);
            }

            boolean success = "success".equals(response.get("status")) && "SUCCESS".equals(response.get("paymentStatus"));
            return new CheckoutResult(success, (String) response.get("conversationId"), (String) response.get("paymentId"));
        } catch (RestClientResponseException e) {
            log.warn("iyzico odeme sonucu sorgulanamadi: durum={}, govde={}", e.getStatusCode(), e.getResponseBodyAsString());
            return new CheckoutResult(false, null, null);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postSigned(String path, String requestBody) {
        return restClient.post()
            .uri(baseUrl + path)
            .header("Authorization", authorizationHeader(path, requestBody))
            .header("Content-Type", "application/json")
            .body(requestBody)
            .retrieve()
            .body(Map.class);
    }

    // paket-ozel: JSON kacislama regresyon testi dogrudan cagirir
    static Map<String, Object> initializeRequestBody(
        String conversationId, BigDecimal amount, String buyerName, String buyerEmail, String callbackUrl
    ) {
        String amountText = amount.toPlainString();

        Map<String, Object> buyer = new LinkedHashMap<>();
        buyer.put("id", conversationId);
        buyer.put("name", buyerName);
        buyer.put("surname", "-");
        buyer.put("gsmNumber", "+905000000000");
        buyer.put("email", buyerEmail);
        buyer.put("identityNumber", "11111111111");
        buyer.put("registrationAddress", "-");
        buyer.put("ip", "127.0.0.1");
        buyer.put("city", "Istanbul");
        buyer.put("country", "Turkey");
        buyer.put("zipCode", "34000");

        Map<String, Object> basketItem = new LinkedHashMap<>();
        basketItem.put("id", conversationId);
        basketItem.put("name", "Vetly Abonelik");
        basketItem.put("category1", "SaaS");
        basketItem.put("itemType", "VIRTUAL");
        basketItem.put("price", amountText);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("locale", "tr");
        body.put("conversationId", conversationId);
        body.put("price", amountText);
        body.put("paidPrice", amountText);
        body.put("currency", "TRY");
        body.put("basketId", conversationId);
        body.put("paymentGroup", "SUBSCRIPTION");
        body.put("callbackUrl", callbackUrl);
        body.put("enabledInstallments", List.of(1));
        body.put("buyer", buyer);
        body.put("shippingAddress", addressBody(buyerName));
        body.put("billingAddress", addressBody(buyerName));
        body.put("basketItems", List.of(basketItem));
        return body;
    }

    private static Map<String, Object> addressBody(String contactName) {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("contactName", contactName);
        address.put("city", "Istanbul");
        address.put("country", "Turkey");
        address.put("address", "-");
        address.put("zipCode", "34000");
        return address;
    }

    /**
     * Istek govdesi Jackson ile serilestirilir -- String.formatted() ile elle
     * kurulan govdede kacislanmamis bir tirnak (public callback'ten gelen token
     * ya da klinik adi) govdeyi bozabilir veya alan enjekte edebilir. HMAC imzasi
     * yine tam olarak bu nihai govde metni uzerinden hesaplanir.
     */
    String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new PaymentGatewayException("iyzico istek govdesi olusturulamadi", e);
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
