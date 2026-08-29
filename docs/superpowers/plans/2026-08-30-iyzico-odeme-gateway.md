# iyzico Ödeme Gateway Entegrasyonu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kiracının kendi `PlatformInvoice`'unu `/ayarlar/abonelik` sayfasından iyzico Checkout Form ile tek seferlik online kart ödemesi yapabilmesini sağlamak.

**Architecture:** Yeni bir `PaymentGatewayPort` (hexagonal port) + `IyzicoPaymentGatewayAdapter` (credential yokken simüle eden, `TwilioNotificationAdapter` ile aynı desen). Checkout başlatma yeni bir use-case; iyzico'nun callback'i geldiğinde sonucu doğrulayıp mevcut `RecordPlatformPaymentUseCase`'i (değişmeden) yeniden kullanan ikinci bir use-case. Frontend'de faturalar tablosuna "Öde" butonu ve ödeme sonrası banner eklenir.

**Tech Stack:** Spring Boot 3.5 / Java 21 (backend, `modules/platformadmin`), React 19 / TypeScript (frontend), PostgreSQL + Flyway.

**Spec:** `docs/superpowers/specs/2026-08-30-iyzico-odeme-gateway-design.md`

## Global Constraints

- iyzico entegrasyonu **Checkout Form (hosted sayfaya tam sayfa yönlendirme)** yöntemiyle yapılır — kart verisi hiçbir zaman backend'e uğramaz.
- `IYZICO_API_KEY`/`IYZICO_SECRET_KEY` boşsa **tüm** gateway çağrıları simüle edilir (gerçek iyzico'ya hiç istek gitmez) — `TwilioNotificationAdapter`'daki `isConfigured()` deseniyle birebir aynı.
- Kayıtlı kart / otomatik tekrarlayan tahsilat **yok** — her fatura ayrı ayrı, elle "Öde" tetiklenerek ödenir.
- vetly.com'daki yeni-kiracı-oluşturma akışı (sub-proje #4) bu planın **kapsamı dışında**.
- Yeni public callback endpoint'i mevcut `/api/v1/public/**` permitAll kuralına girer — `SecurityConfig.java` **değişmez**.
- Son kullanılan migration `V27` — bu plan `V28` ile devam eder.
- Bu repoda controller-seviyesinde otomatik test altyapısı (MockMvc vb.) **yok** — mevcut pratik: use-case/domain seviyesinde JUnit+Mockito birim testi + çalışan bir instance'a karşı curl ile uçtan uca doğrulama. Bu plan da aynı yöntemi izler.
- `LocalDate.now()` gibi saat okuma çağrıları use-case içine gömülmez — `PlatformBillingScheduler`'daki desende olduğu gibi, çağıran sınır (controller) `today`'i parametre olarak geçirir.

---

### Task 1: Domain modeli — `CARD_ONLINE` ödeme yöntemi + nullable admin id

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformPaymentMethod.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformPayment.java`
- Create: `backend/src/main/resources/db/migration/V28__payment_gateway.sql`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/domain/PlatformPaymentTest.java` (mevcut dosyaya yeni test eklenir)

**Interfaces:**
- Produces: `PlatformPaymentMethod.CARD_ONLINE` (enum değeri), `PlatformPayment.record(invoiceId, amount, method, paidAt, recordedByAdminId, notes)` artık `recordedByAdminId = null` ile çağrılabilir.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/vetos/modules/platformadmin/domain/PlatformPaymentTest.java` dosyasının sonuna (mevcut `should_populateAllFields_when_recorded` testinin altına, kapanış `}`'den önce) ekle:

```java
    @Test
    void should_allowNullRecordedByAdminId_when_gatewayInitiatedPayment() {
        UUID invoiceId = UUID.randomUUID();
        LocalDate paidAt = LocalDate.of(2026, 8, 30);

        PlatformPayment payment = PlatformPayment.record(
            invoiceId, new BigDecimal("500.00"), PlatformPaymentMethod.CARD_ONLINE, paidAt, null, "iyzico odeme referansi: pay_123"
        );

        assertThat(payment.getRecordedByAdminId()).isNull();
        assertThat(payment.getMethod()).isEqualTo(PlatformPaymentMethod.CARD_ONLINE);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `backend/`): `./mvnw -q -o test -Dtest=PlatformPaymentTest`
Expected: derleme hatası (`CARD_ONLINE` enum'da yok — `cannot find symbol`).

- [ ] **Step 3: Add `CARD_ONLINE` to the enum**

`backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformPaymentMethod.java` — tüm dosyanın yeni hali:

```java
package com.vetos.modules.platformadmin.domain;

public enum PlatformPaymentMethod { BANK_TRANSFER, CARD, CARD_ONLINE, OTHER }
```

- [ ] **Step 4: Make `recordedByAdminId` nullable in the entity**

`backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformPayment.java` içinde şu satırı bul:

```java
    @Column(name = "recorded_by_admin_id", nullable = false)
    private UUID recordedByAdminId;
```

ve şununla değiştir:

```java
    @Column(name = "recorded_by_admin_id")
    private UUID recordedByAdminId;
```

- [ ] **Step 5: Write the migration**

`backend/src/main/resources/db/migration/V28__payment_gateway.sql` (yeni dosya):

```sql
ALTER TABLE platform_payments ALTER COLUMN recorded_by_admin_id DROP NOT NULL;
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw -q -o test -Dtest=PlatformPaymentTest`
Expected: PASS (2 test, 0 hata) — not: bu birim testi gerçek bir veritabanına dokunmaz (JPA entity'si düz Java nesnesi olarak test ediliyor), migration'ın gerçek etkisi Task 5'teki uçtan uca doğrulamada görülecek.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformPaymentMethod.java backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformPayment.java backend/src/main/resources/db/migration/V28__payment_gateway.sql backend/src/test/java/com/vetos/modules/platformadmin/domain/PlatformPaymentTest.java
git commit -m "feat: add CARD_ONLINE payment method and nullable admin id for gateway payments"
```

---

### Task 2: `PaymentGatewayPort` + iyzico adaptörü (simüle + gerçek mod)

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/PaymentGatewayPort.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/CheckoutSession.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/CheckoutResult.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/PaymentGatewayException.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/IyzicoPaymentGatewayAdapter.java`
- Create: `backend/src/test/java/com/vetos/modules/platformadmin/infrastructure/adapter/IyzicoPaymentGatewayAdapterTest.java`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Consumes: hiçbir önceki task'a bağımlı değil (domain paketi, kendi kendine yeter).
- Produces: `PaymentGatewayPort.isConfigured(): boolean`, `PaymentGatewayPort.initializeCheckout(String conversationId, BigDecimal amount, String buyerName, String buyerEmail): CheckoutSession`, `PaymentGatewayPort.retrieveCheckoutResult(String token): CheckoutResult`. `CheckoutSession(String checkoutFormUrl, String token)`. `CheckoutResult(boolean success, String conversationId, String paymentId)`. Task 3 ve 4 bu port'u ve record'ları kullanacak.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/vetos/modules/platformadmin/infrastructure/adapter/IyzicoPaymentGatewayAdapterTest.java` (yeni dosya, yeni `infrastructure/adapter` test paketi):

```java
package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.CheckoutResult;
import com.vetos.modules.platformadmin.domain.CheckoutSession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IyzicoPaymentGatewayAdapterTest {

    private final IyzicoPaymentGatewayAdapter adapter =
        new IyzicoPaymentGatewayAdapter("", "", "https://sandbox-api.iyzipay.com", "http://localhost:8080");

    @Test
    void should_notBeConfigured_when_credentialsAreBlank() {
        assertThat(adapter.isConfigured()).isFalse();
    }

    @Test
    void should_returnSimulatedCheckoutFormUrl_when_notConfigured() {
        String conversationId = UUID.randomUUID().toString();

        CheckoutSession session = adapter.initializeCheckout(conversationId, new BigDecimal("500.00"), "Test Klinik", "test@example.com");

        assertThat(session.checkoutFormUrl())
            .isEqualTo("http://localhost:8080/api/v1/public/payments/iyzico/callback?token=SIMULATED-" + conversationId);
        assertThat(session.token()).isEqualTo("SIMULATED-" + conversationId);
    }

    @Test
    void should_returnSuccessfulResult_when_retrievingSimulatedToken() {
        String conversationId = UUID.randomUUID().toString();

        CheckoutResult result = adapter.retrieveCheckoutResult("SIMULATED-" + conversationId);

        assertThat(result.success()).isTrue();
        assertThat(result.conversationId()).isEqualTo(conversationId);
        assertThat(result.paymentId()).isNotBlank();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -o test -Dtest=IyzicoPaymentGatewayAdapterTest`
Expected: derleme hatası (`IyzicoPaymentGatewayAdapter`, `CheckoutSession`, `CheckoutResult` henüz yok).

- [ ] **Step 3: Create the port and value types**

`backend/src/main/java/com/vetos/modules/platformadmin/domain/PaymentGatewayPort.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.domain;

import java.math.BigDecimal;

/**
 * Odeme gateway'ini soyutlayan port -- TARBIL/e-posta/SMS portlariyla ayni
 * desen (@docs/architecture.md Bolum 3). Gercek bir iyzico hesabi bu ortamda
 * henuz yok; IyzicoPaymentGatewayAdapter kimlik bilgisi tanimli degilken
 * bu portu simule eder.
 */
public interface PaymentGatewayPort {
    boolean isConfigured();
    CheckoutSession initializeCheckout(String conversationId, BigDecimal amount, String buyerName, String buyerEmail);
    CheckoutResult retrieveCheckoutResult(String token);
}
```

`backend/src/main/java/com/vetos/modules/platformadmin/domain/CheckoutSession.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.domain;

/** iyzico Checkout Form baslatma sonucu -- checkoutFormUrl'e tarayici tam sayfa yonlendirilir. */
public record CheckoutSession(String checkoutFormUrl, String token) {}
```

`backend/src/main/java/com/vetos/modules/platformadmin/domain/CheckoutResult.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.domain;

/** iyzico'nun callback sonrasi "sonucu sorgula" cevabi. conversationId, initializeCheckout'a gecirilen invoice id'dir. */
public record CheckoutResult(boolean success, String conversationId, String paymentId) {}
```

`backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/PaymentGatewayException.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

public class PaymentGatewayException extends DomainException {
    public PaymentGatewayException(String message, Throwable cause) {
        super("PAYMENT_GATEWAY_ERROR", "Odeme baslatilamadi, lutfen tekrar deneyin");
        initCause(cause);
    }
}
```

- [ ] **Step 4: Implement the adapter**

`backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/IyzicoPaymentGatewayAdapter.java` (yeni dosya):

```java
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
```

- [ ] **Step 5: Add config block to `application.yml`**

`backend/src/main/resources/application.yml` sonuna (mevcut `platform-billing` bloğunun altına) ekle:

```yaml
payment-gateway:
  iyzico:
    # UYARI: bos birakilirsa checkout tamamen simule edilir ve
    # /api/v1/public/payments/iyzico/callback GET ile "?token=SIMULATED-<herhangi-bir-fatura-id>"
    # cagrilarak HERHANGI BIR FATURA BEDAVA ODENMIS ISARETLENEBILIR (bkz.
    # IyzicoPaymentGatewayAdapter.isConfigured()) -- gelistirme/CI icin
    # bilincli bir tasarim, ama prod'da IYZICO_API_KEY/IYZICO_SECRET_KEY
    # mutlaka set edilmeli (JWT_SECRET/PLATFORM_ADMIN_PASSWORD ile ayni
    # operasyonel gereklilik).
    api-key: ${IYZICO_API_KEY:}
    secret-key: ${IYZICO_SECRET_KEY:}
    base-url: ${IYZICO_BASE_URL:https://sandbox-api.iyzipay.com}
    # iyzico'nun callback POST'u bu adrese gelir -- prod'da backend'in
    # disaridan erisilebilir gercek adresiyle override edilmeli.
    callback-base-url: ${IYZICO_CALLBACK_BASE_URL:http://localhost:8080}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw -q -o test -Dtest=IyzicoPaymentGatewayAdapterTest`
Expected: PASS (3 test, 0 hata).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/domain/PaymentGatewayPort.java backend/src/main/java/com/vetos/modules/platformadmin/domain/CheckoutSession.java backend/src/main/java/com/vetos/modules/platformadmin/domain/CheckoutResult.java backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/PaymentGatewayException.java backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/IyzicoPaymentGatewayAdapter.java backend/src/test/java/com/vetos/modules/platformadmin/infrastructure/adapter/IyzicoPaymentGatewayAdapterTest.java backend/src/main/resources/application.yml
git commit -m "feat: add PaymentGatewayPort and iyzico Checkout Form adapter"
```

---

### Task 3: `InitiateInvoiceCheckoutUseCase`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/InitiateInvoiceCheckoutUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/application/InitiateInvoiceCheckoutUseCaseTest.java`

**Interfaces:**
- Consumes: `PlatformInvoiceRepository.findById(UUID): Optional<PlatformInvoice>` (mevcut), `TenantAdminPort.getOverview(UUID): TenantAdminOverview` ve `TenantAdminPort.findBillingContactEmail(UUID): Optional<String>` (mevcut, `com.vetos.modules.tenant.domain`), `PaymentGatewayPort.initializeCheckout(String, BigDecimal, String, String): CheckoutSession` (Task 2).
- Produces: `InitiateInvoiceCheckoutUseCase.execute(UUID tenantId, UUID invoiceId): CheckoutSession` — Task 5'teki `TenantBillingController` bunu çağıracak.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/vetos/modules/platformadmin/application/InitiateInvoiceCheckoutUseCaseTest.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.*;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceInvalidTransitionException;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitiateInvoiceCheckoutUseCaseTest {

    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private PaymentGatewayPort paymentGatewayPort;

    private InitiateInvoiceCheckoutUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new InitiateInvoiceCheckoutUseCase(platformInvoiceRepository, tenantAdminPort, paymentGatewayPort);
    }

    @Test
    void should_initializeCheckout_when_invoiceIsIssuedAndOwnedByTenant() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 30);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId));
        when(tenantAdminPort.findBillingContactEmail(tenantId)).thenReturn(Optional.of("klinik@example.com"));
        CheckoutSession expectedSession = new CheckoutSession("https://sandbox.iyzipay.com/pay/abc", "abc");
        when(paymentGatewayPort.initializeCheckout(invoice.getId().toString(), invoice.getAmount(), "Test Klinik", "klinik@example.com"))
            .thenReturn(expectedSession);

        CheckoutSession result = useCase.execute(tenantId, invoice.getId());

        assertThat(result).isEqualTo(expectedSession);
    }

    @Test
    void should_fallBackToDefaultEmail_when_noBillingContactEmail() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 30);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId));
        when(tenantAdminPort.findBillingContactEmail(tenantId)).thenReturn(Optional.empty());
        when(paymentGatewayPort.initializeCheckout(eq(invoice.getId().toString()), any(), any(), eq("destek@myvet.app")))
            .thenReturn(new CheckoutSession("https://sandbox.iyzipay.com/pay/abc", "abc"));

        useCase.execute(tenantId, invoice.getId());
    }

    @Test
    void should_throwNotFound_when_invoiceBelongsToAnotherTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 30);
        PlatformInvoice invoice = PlatformInvoice.issue(otherTenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> useCase.execute(tenantId, invoice.getId()))
            .isInstanceOf(PlatformInvoiceNotFoundException.class);
    }

    @Test
    void should_throwNotFound_when_invoiceDoesNotExist() {
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        when(platformInvoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(tenantId, invoiceId))
            .isInstanceOf(PlatformInvoiceNotFoundException.class);
    }

    @Test
    void should_throwInvalidTransition_when_invoiceAlreadyPaid() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 30);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        invoice.markPaid();
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> useCase.execute(tenantId, invoice.getId()))
            .isInstanceOf(PlatformInvoiceInvalidTransitionException.class);
    }

    private TenantAdminOverview overview(UUID tenantId) {
        return new TenantAdminOverview(
            tenantId, "Test Klinik", "123", TenantStatus.ACTIVE, Instant.now(), "PRO", BillingStatus.PAST_DUE,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 30), 1, 3
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -o test -Dtest=InitiateInvoiceCheckoutUseCaseTest`
Expected: derleme hatası (`InitiateInvoiceCheckoutUseCase` henüz yok).

- [ ] **Step 3: Implement the use case**

`backend/src/main/java/com/vetos/modules/platformadmin/application/InitiateInvoiceCheckoutUseCase.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.CheckoutSession;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceInvalidTransitionException;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InitiateInvoiceCheckoutUseCase {

    private static final String FALLBACK_BUYER_EMAIL = "destek@myvet.app";

    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final TenantAdminPort tenantAdminPort;
    private final PaymentGatewayPort paymentGatewayPort;

    @Transactional(readOnly = true)
    public CheckoutSession execute(UUID tenantId, UUID invoiceId) {
        PlatformInvoice invoice = platformInvoiceRepository.findById(invoiceId)
            .filter(i -> i.getTenantId().equals(tenantId))
            .orElseThrow(() -> new PlatformInvoiceNotFoundException(invoiceId));

        if (invoice.getStatus() != PlatformInvoiceStatus.ISSUED && invoice.getStatus() != PlatformInvoiceStatus.OVERDUE) {
            throw new PlatformInvoiceInvalidTransitionException(invoice.getStatus(), PlatformInvoiceStatus.PAID);
        }

        TenantAdminOverview overview = tenantAdminPort.getOverview(tenantId);
        String buyerEmail = tenantAdminPort.findBillingContactEmail(tenantId).orElse(FALLBACK_BUYER_EMAIL);

        return paymentGatewayPort.initializeCheckout(invoice.getId().toString(), invoice.getAmount(), overview.name(), buyerEmail);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -o test -Dtest=InitiateInvoiceCheckoutUseCaseTest`
Expected: PASS (5 test, 0 hata).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/application/InitiateInvoiceCheckoutUseCase.java backend/src/test/java/com/vetos/modules/platformadmin/application/InitiateInvoiceCheckoutUseCaseTest.java
git commit -m "feat: add InitiateInvoiceCheckoutUseCase"
```

---

### Task 4: `HandlePaymentCallbackUseCase`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/HandlePaymentCallbackUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/application/HandlePaymentCallbackUseCaseTest.java`

**Interfaces:**
- Consumes: `PaymentGatewayPort.retrieveCheckoutResult(String): CheckoutResult` (Task 2), `PlatformInvoiceRepository.findById(UUID): Optional<PlatformInvoice>` (mevcut), `RecordPlatformPaymentUseCase.execute(RecordPlatformPaymentCommand)` (mevcut, değişmedi), `RecordPlatformPaymentCommand(UUID invoiceId, BigDecimal amount, PlatformPaymentMethod method, LocalDate paidAt, String notes, UUID recordedByAdminId)` (mevcut).
- Produces: `HandlePaymentCallbackUseCase.execute(String token, LocalDate today): boolean` (true = ödeme başarıyla işlendi veya zaten işlenmişti; false = ödeme başarısız) — Task 5'teki `PublicPaymentCallbackController` bunu çağıracak.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/vetos/modules/platformadmin/application/HandlePaymentCallbackUseCaseTest.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.modules.platformadmin.domain.*;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceInvalidTransitionException;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HandlePaymentCallbackUseCaseTest {

    @Mock private PaymentGatewayPort paymentGatewayPort;
    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private RecordPlatformPaymentUseCase recordPlatformPaymentUseCase;

    private HandlePaymentCallbackUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new HandlePaymentCallbackUseCase(paymentGatewayPort, platformInvoiceRepository, recordPlatformPaymentUseCase);
    }

    @Test
    void should_recordCardOnlinePayment_when_gatewayReportsSuccess() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 30);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        when(paymentGatewayPort.retrieveCheckoutResult("tok-1"))
            .thenReturn(new CheckoutResult(true, invoice.getId().toString(), "pay_123"));
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        boolean result = useCase.execute("tok-1", today);

        assertThat(result).isTrue();
        ArgumentCaptor<RecordPlatformPaymentCommand> captor = ArgumentCaptor.forClass(RecordPlatformPaymentCommand.class);
        verify(recordPlatformPaymentUseCase).execute(captor.capture());
        RecordPlatformPaymentCommand command = captor.getValue();
        assertThat(command.invoiceId()).isEqualTo(invoice.getId());
        assertThat(command.amount()).isEqualByComparingTo("500.00");
        assertThat(command.method()).isEqualTo(PlatformPaymentMethod.CARD_ONLINE);
        assertThat(command.recordedByAdminId()).isNull();
        assertThat(command.notes()).contains("pay_123");
    }

    @Test
    void should_returnFalse_when_gatewayReportsFailure() {
        when(paymentGatewayPort.retrieveCheckoutResult("tok-2"))
            .thenReturn(new CheckoutResult(false, null, null));

        boolean result = useCase.execute("tok-2", LocalDate.of(2026, 8, 30));

        assertThat(result).isFalse();
        verifyNoInteractions(recordPlatformPaymentUseCase);
    }

    @Test
    void should_returnTrue_when_invoiceAlreadyPaid_repeatedCallback() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 30);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        when(paymentGatewayPort.retrieveCheckoutResult("tok-3"))
            .thenReturn(new CheckoutResult(true, invoice.getId().toString(), "pay_123"));
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        doThrow(new PlatformInvoiceInvalidTransitionException(PlatformInvoiceStatus.PAID, PlatformInvoiceStatus.PAID))
            .when(recordPlatformPaymentUseCase).execute(any());

        boolean result = useCase.execute("tok-3", today);

        assertThat(result).isTrue();
    }

    @Test
    void should_throwNotFound_when_conversationIdDoesNotMatchAnyInvoice() {
        UUID unknownId = UUID.randomUUID();
        when(paymentGatewayPort.retrieveCheckoutResult("tok-4"))
            .thenReturn(new CheckoutResult(true, unknownId.toString(), "pay_123"));
        when(platformInvoiceRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("tok-4", LocalDate.of(2026, 8, 30)))
            .isInstanceOf(PlatformInvoiceNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -o test -Dtest=HandlePaymentCallbackUseCaseTest`
Expected: derleme hatası (`HandlePaymentCallbackUseCase` henüz yok).

- [ ] **Step 3: Implement the use case**

`backend/src/main/java/com/vetos/modules/platformadmin/application/HandlePaymentCallbackUseCase.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.modules.platformadmin.domain.CheckoutResult;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformPaymentMethod;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceInvalidTransitionException;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * iyzico'nun checkout callback'i sonrasi cagrilir. Idempotent: ayni token
 * (veya invoice zaten PAID) icin tekrar cagrilirsa RecordPlatformPaymentUseCase'in
 * firlattigi PlatformInvoiceInvalidTransitionException yutulur -- iyzico'nun
 * kendi retry mekanizmasi 500 almasin diye.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HandlePaymentCallbackUseCase {

    private final PaymentGatewayPort paymentGatewayPort;
    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final RecordPlatformPaymentUseCase recordPlatformPaymentUseCase;

    @Transactional
    public boolean execute(String token, LocalDate today) {
        CheckoutResult result = paymentGatewayPort.retrieveCheckoutResult(token);
        if (!result.success()) {
            log.info("iyzico odeme basarisiz: conversationId={}", result.conversationId());
            return false;
        }

        UUID invoiceId = UUID.fromString(result.conversationId());
        PlatformInvoice invoice = platformInvoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new PlatformInvoiceNotFoundException(invoiceId));

        try {
            recordPlatformPaymentUseCase.execute(new RecordPlatformPaymentCommand(
                invoice.getId(), invoice.getAmount(), PlatformPaymentMethod.CARD_ONLINE,
                today, "iyzico odeme referansi: " + result.paymentId(), null
            ));
        } catch (PlatformInvoiceInvalidTransitionException e) {
            log.info("iyzico callback tekrarlandi, fatura zaten cozumlenmis: invoiceId={}", invoiceId);
        }
        return true;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -o test -Dtest=HandlePaymentCallbackUseCaseTest`
Expected: PASS (4 test, 0 hata).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/application/HandlePaymentCallbackUseCase.java backend/src/test/java/com/vetos/modules/platformadmin/application/HandlePaymentCallbackUseCaseTest.java
git commit -m "feat: add HandlePaymentCallbackUseCase with idempotent retry handling"
```

---

### Task 5: Controller uç noktaları + uçtan uca doğrulama

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/api/dto/CheckoutSessionResponse.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/api/PublicPaymentCallbackController.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/api/TenantBillingController.java`

**Interfaces:**
- Consumes: `InitiateInvoiceCheckoutUseCase.execute(UUID, UUID): CheckoutSession` (Task 3), `HandlePaymentCallbackUseCase.execute(String, LocalDate): boolean` (Task 4), `TenantContext.current(): UUID` (mevcut, `com.vetos.platform.tenancy`).
- Produces: `POST /api/v1/subscriptions/invoices/{invoiceId}/checkout` → `{ "checkoutFormUrl": string }`. `POST` ve `GET /api/v1/public/payments/iyzico/callback?token=...` → 302 redirect. Task 6 (frontend) bu iki uç noktayı kullanacak.

- [ ] **Step 1: Add the response DTO**

`backend/src/main/java/com/vetos/modules/platformadmin/api/dto/CheckoutSessionResponse.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.api.dto;

public record CheckoutSessionResponse(String checkoutFormUrl) {}
```

- [ ] **Step 2: Add the checkout endpoint to `TenantBillingController`**

`backend/src/main/java/com/vetos/modules/platformadmin/api/TenantBillingController.java` — tüm dosyanın yeni hali:

```java
package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.CheckoutSessionResponse;
import com.vetos.modules.platformadmin.api.dto.PlanResponse;
import com.vetos.modules.platformadmin.api.dto.PlatformInvoiceResponse;
import com.vetos.modules.platformadmin.api.dto.TenantBillingOverviewResponse;
import com.vetos.modules.platformadmin.application.InitiateInvoiceCheckoutUseCase;
import com.vetos.modules.platformadmin.application.ListPlansUseCase;
import com.vetos.modules.platformadmin.application.ListPlatformInvoicesForTenantUseCase;
import com.vetos.modules.platformadmin.domain.CheckoutSession;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.platform.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Kiracinin (klinigin) KENDI platform faturalarini goruntuleyebilmesi icin --
 * PlatformInvoicesController'dan (platform-admin, PLATFORM_ADMIN-only) farkli
 * olarak bu controller normal tenant JWT'siyle (ADMIN rolu) korunur ve
 * tenantId'yi istek govdesinden/URL'den DEGIL, TenantContext.current()'tan
 * alir -- baska bir kiracinin faturalarini asla gosteremez.
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class TenantBillingController {

    private final ListPlatformInvoicesForTenantUseCase listPlatformInvoicesForTenantUseCase;
    private final ListPlansUseCase listPlansUseCase;
    private final InitiateInvoiceCheckoutUseCase initiateInvoiceCheckoutUseCase;
    private final String paymentInstructions;

    TenantBillingController(
        ListPlatformInvoicesForTenantUseCase listPlatformInvoicesForTenantUseCase,
        ListPlansUseCase listPlansUseCase,
        InitiateInvoiceCheckoutUseCase initiateInvoiceCheckoutUseCase,
        @Value("${platform-billing.payment-instructions}") String paymentInstructions
    ) {
        this.listPlatformInvoicesForTenantUseCase = listPlatformInvoicesForTenantUseCase;
        this.listPlansUseCase = listPlansUseCase;
        this.initiateInvoiceCheckoutUseCase = initiateInvoiceCheckoutUseCase;
        this.paymentInstructions = paymentInstructions;
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasRole('ADMIN')")
    public TenantBillingOverviewResponse invoices() {
        var invoices = listPlatformInvoicesForTenantUseCase.execute(TenantContext.current()).stream()
            .map(PlatformInvoiceResponse::from)
            .toList();
        return new TenantBillingOverviewResponse(paymentInstructions, invoices);
    }

    /**
     * Plan katalogunu (aktif planlar) tenant'a gosterir -- abonelik panelinde
     * kendi planinin aciklama/ozellik/badge bilgilerini eslestirmek icin kullanilir.
     */
    @GetMapping("/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PlanResponse> plans() {
        return listPlansUseCase.execute().stream()
            .filter(Plan::isActive)
            .map(PlanResponse::from)
            .toList();
    }

    @PostMapping("/invoices/{invoiceId}/checkout")
    @PreAuthorize("hasRole('ADMIN')")
    public CheckoutSessionResponse checkout(@PathVariable UUID invoiceId) {
        CheckoutSession session = initiateInvoiceCheckoutUseCase.execute(TenantContext.current(), invoiceId);
        return new CheckoutSessionResponse(session.checkoutFormUrl());
    }
}
```

- [ ] **Step 3: Add the public callback controller**

`backend/src/main/java/com/vetos/modules/platformadmin/api/PublicPaymentCallbackController.java` (yeni dosya):

```java
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
```

- [ ] **Step 4: Compile and run the full backend test suite**

Run (from `backend/`): `./mvnw -q -o test-compile`
Expected: derleme hatasız biter (çıktı yok).

Run: `./mvnw -q -o test -Dtest=ApplicationModulesTest`
Expected: PASS — `platformadmin` modülünün `allowedDependencies`'i bu görevde değişmedi (`TenantAdminPort`/`TenantContext` zaten izinliydi), yeni bir sınır ihlali beklenmez.

- [ ] **Step 5: Uçtan uca doğrulama (simüle mod, gerçek çalışan instance'a karşı)**

Backend'i başlat (proje kökünden): `cd backend && ./mvnw spring-boot:run` (arka planda, ayrı bir terminalde/arka plan görevinde).

Platform admin ile giriş yap ve bir test faturası bul/üret (mevcut platform admin panelinden veya curl ile — `POST /api/v1/platform-admin/auth/login`, ardından test tenant'ı ve faturasını `GET /api/v1/platform-admin/tenants/{id}`'den bul). Sonra tenant admin JWT'siyle:

```bash
# tenant admin token'ini al
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"<tenant-admin-email>","password":"<tenant-admin-password>"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# ISSUED/OVERDUE bir faturanin id'sini al
curl -s http://localhost:8080/api/v1/subscriptions/invoices -H "Authorization: Bearer $TOKEN"

# checkout baslat
CHECKOUT=$(curl -s -X POST http://localhost:8080/api/v1/subscriptions/invoices/<invoiceId>/checkout \
  -H "Authorization: Bearer $TOKEN")
echo "$CHECKOUT"
# beklenen: {"checkoutFormUrl":"http://localhost:8080/api/v1/public/payments/iyzico/callback?token=SIMULATED-<invoiceId>"}

# donen checkoutFormUrl'e git (simule callback, GET, redirect'i takip et)
curl -sI "http://localhost:8080/api/v1/public/payments/iyzico/callback?token=SIMULATED-<invoiceId>"
# beklenen: 302, Location: http://localhost:5173/ayarlar/abonelik?odeme=basarili

# faturanin PAID oldugunu dogrula
curl -s http://localhost:8080/api/v1/subscriptions/invoices -H "Authorization: Bearer $TOKEN"
# beklenen: ilgili faturanin status'u artik "PAID"

# tekrar ayni callback'i cagirip idempotent oldugunu dogrula (500 DEGIL, yine 302 basarili)
curl -sI "http://localhost:8080/api/v1/public/payments/iyzico/callback?token=SIMULATED-<invoiceId>"
```

Backend'i durdur.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/api/dto/CheckoutSessionResponse.java backend/src/main/java/com/vetos/modules/platformadmin/api/PublicPaymentCallbackController.java backend/src/main/java/com/vetos/modules/platformadmin/api/TenantBillingController.java
git commit -m "feat: add checkout and public payment callback endpoints"
```

---

### Task 6: Frontend — "Öde" butonu ve ödeme sonucu banner'ı

**Files:**
- Modify: `frontend/src/api/subscriptionApi.ts`
- Modify: `frontend/src/pages/settings/SubscriptionPanel.tsx`
- Modify: `frontend/src/pages/settings/SubscriptionPanel.module.css`

**Interfaces:**
- Consumes: `POST /api/v1/subscriptions/invoices/{invoiceId}/checkout` (Task 5).
- Produces: `subscriptionApi.initiateCheckout(invoiceId: string): Promise<{ checkoutFormUrl: string }>`.

- [ ] **Step 1: Add `initiateCheckout` to `subscriptionApi.ts`**

`frontend/src/api/subscriptionApi.ts` içindeki `export const subscriptionApi = { ... };` bloğunu bul ve şununla değiştir (dosyanın geri kalanı aynı kalır):

```ts
export interface CheckoutSession {
  checkoutFormUrl: string;
}

export const subscriptionApi = {
  getCurrent: () => apiClient.get<SubscriptionOverview>('/api/v1/subscriptions/current'),
  getInvoices: () => apiClient.get<TenantBillingOverview>('/api/v1/subscriptions/invoices'),
  getPlans: () => apiClient.get<CatalogPlan[]>('/api/v1/subscriptions/plans'),
  initiateCheckout: (invoiceId: string) =>
    apiClient.post<CheckoutSession>(`/api/v1/subscriptions/invoices/${invoiceId}/checkout`),
};
```

- [ ] **Step 2: Update `SubscriptionPanel.tsx`**

Tüm dosyanın yeni hali (`frontend/src/pages/settings/SubscriptionPanel.tsx`):

```tsx
import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import {
  BillingStatus, CatalogPlan, PlatformInvoice, PlatformInvoiceStatus, SubscriptionOverview, subscriptionApi,
} from '../../api/subscriptionApi';
import { Badge, BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import settingsStyles from './SettingsPage.module.css';
import styles from './SubscriptionPanel.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

const STATUS_LABELS: Record<BillingStatus, string> = {
  TRIAL: 'Deneme Sürümü',
  ACTIVE: 'Aktif',
  PAST_DUE: 'Ödeme Gecikti',
  CANCELED: 'İptal Edildi',
};

const STATUS_TONES: Record<BillingStatus, BadgeTone> = {
  TRIAL: 'neutral',
  ACTIVE: 'success',
  PAST_DUE: 'warning',
  CANCELED: 'danger',
};

const INVOICE_STATUS_LABELS: Record<PlatformInvoiceStatus, string> = {
  ISSUED: 'Kesildi',
  PAID: 'Ödendi',
  OVERDUE: 'Gecikti',
  VOID: 'İptal',
};

const INVOICE_STATUS_TONES: Record<PlatformInvoiceStatus, BadgeTone> = {
  ISSUED: 'neutral',
  PAID: 'success',
  OVERDUE: 'danger',
  VOID: 'neutral',
};

function formatDate(value: string | null): string {
  if (!value) return '—';
  return new Date(value).toLocaleDateString('tr-TR');
}

function isPayable(status: PlatformInvoiceStatus): boolean {
  return status === 'ISSUED' || status === 'OVERDUE';
}

export function SubscriptionPanel() {
  const [subscription, setSubscription] = useState<SubscriptionOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [invoices, setInvoices] = useState<PlatformInvoice[]>([]);
  const [paymentInstructions, setPaymentInstructions] = useState('');
  const [invoicesLoading, setInvoicesLoading] = useState(true);
  const [invoicesError, setInvoicesError] = useState<string | null>(null);

  const [plans, setPlans] = useState<CatalogPlan[]>([]);

  const [checkoutLoadingId, setCheckoutLoadingId] = useState<string | null>(null);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [paymentResult, setPaymentResult] = useState<'basarili' | 'hata' | null>(null);

  function loadInvoices() {
    setInvoicesLoading(true);
    subscriptionApi
      .getInvoices()
      .then((overview) => {
        setInvoices(overview.invoices);
        setPaymentInstructions(overview.paymentInstructions);
      })
      .catch((err) => setInvoicesError(errorMessageOf(err)))
      .finally(() => setInvoicesLoading(false));
  }

  useEffect(() => {
    subscriptionApi
      .getCurrent()
      .then(setSubscription)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));

    loadInvoices();

    subscriptionApi.getPlans().then(setPlans).catch(() => setPlans([]));

    const params = new URLSearchParams(window.location.search);
    const odeme = params.get('odeme');
    if (odeme === 'basarili' || odeme === 'hata') {
      setPaymentResult(odeme);
      params.delete('odeme');
      const newSearch = params.toString();
      window.history.replaceState({}, '', window.location.pathname + (newSearch ? `?${newSearch}` : ''));
    }
  }, []);

  async function handlePay(invoiceId: string) {
    setCheckoutLoadingId(invoiceId);
    setCheckoutError(null);
    try {
      const session = await subscriptionApi.initiateCheckout(invoiceId);
      window.location.href = session.checkoutFormUrl;
    } catch (err) {
      setCheckoutError(errorMessageOf(err));
      setCheckoutLoadingId(null);
    }
  }

  if (loading) {
    return <div className={settingsStyles.empty}>Yükleniyor...</div>;
  }

  if (error || !subscription) {
    return <div className={settingsStyles.errorBanner}>{error ?? 'Abonelik bilgisi bulunamadı'}</div>;
  }

  const hasUnpaidInvoice = invoices.some((inv) => isPayable(inv.status));
  const currentPlan = plans.find((p) => p.code === subscription.planCode) ?? null;

  return (
    <>
      {paymentResult === 'basarili' && (
        <div className={styles.paymentSuccessBanner}>Ödemeniz alındı, teşekkürler.</div>
      )}
      {paymentResult === 'hata' && (
        <div className={settingsStyles.errorBanner}>Ödeme tamamlanamadı, lütfen tekrar deneyin.</div>
      )}

      <Card className={styles.card}>
        {currentPlan?.imageUrl && (
          <img src={currentPlan.imageUrl} alt="" className={styles.planImage} />
        )}
        <div className={styles.planCode}>{currentPlan?.name ?? subscription.planCode}</div>
        {currentPlan?.badge && <span className={styles.planBadge}>{currentPlan.badge}</span>}
        <Badge tone={STATUS_TONES[subscription.billingStatus]}>{STATUS_LABELS[subscription.billingStatus]}</Badge>

        {currentPlan?.description && <p className={styles.planDescription}>{currentPlan.description}</p>}

        {currentPlan && currentPlan.features.length > 0 && (
          <ul className={styles.planFeatureList}>
            {currentPlan.features.map((feature) => (
              <li key={feature}>{feature}</li>
            ))}
          </ul>
        )}

        <div className={styles.row}>
          <span className={styles.rowLabel}>Başlangıç Tarihi</span>
          <span>{formatDate(subscription.startedAt)}</span>
        </div>
        <div className={styles.row}>
          <span className={styles.rowLabel}>Yenileme Tarihi</span>
          <span>{formatDate(subscription.renewsAt)}</span>
        </div>

        <div className={styles.ctaRow}>
          <div className={styles.ctaHint}>
            Planınızı değiştirmek veya faturalama bilgilerinizi güncellemek için bizimle iletişime geçin.
          </div>
          <Button variant="secondary" onClick={() => window.open('mailto:destek@myvet.app')}>
            Bizimle İletişime Geçin
          </Button>
        </div>
      </Card>

      <Card className={styles.invoicesCard}>
        <div className={styles.invoicesTitle}>Faturalar</div>

        {invoicesLoading ? (
          <div className={settingsStyles.empty}>Yükleniyor...</div>
        ) : invoicesError ? (
          <div className={settingsStyles.errorBanner}>{invoicesError}</div>
        ) : (
          <>
            {hasUnpaidInvoice && paymentInstructions && (
              <div className={styles.paymentInstructions}>{paymentInstructions}</div>
            )}
            {checkoutError && <div className={settingsStyles.errorBanner}>{checkoutError}</div>}

            {invoices.length === 0 ? (
              <div className={settingsStyles.empty}>Henüz fatura kesilmedi</div>
            ) : (
              <>
                <div className={`${styles.invoiceRow} ${styles.invoiceRowHead}`}>
                  <div>Dönem</div>
                  <div>Tutar</div>
                  <div>Son Ödeme</div>
                  <div>Durum</div>
                  <div></div>
                </div>
                {invoices.map((inv) => (
                  <div key={inv.id} className={styles.invoiceRow}>
                    <div>
                      {formatDate(inv.periodStart)} — {formatDate(inv.periodEnd)}
                    </div>
                    <div className={styles.muted}>{inv.amount.toFixed(2)} ₺</div>
                    <div className={styles.muted}>{formatDate(inv.dueDate)}</div>
                    <div>
                      <Badge tone={INVOICE_STATUS_TONES[inv.status]}>{INVOICE_STATUS_LABELS[inv.status]}</Badge>
                    </div>
                    <div>
                      {isPayable(inv.status) && (
                        <Button
                          variant="secondary"
                          onClick={() => handlePay(inv.id)}
                          disabled={checkoutLoadingId === inv.id}
                        >
                          {checkoutLoadingId === inv.id ? 'Yönlendiriliyor...' : 'Öde'}
                        </Button>
                      )}
                    </div>
                  </div>
                ))}
              </>
            )}
          </>
        )}
      </Card>
    </>
  );
}
```

- [ ] **Step 3: Update `SubscriptionPanel.module.css`**

`frontend/src/pages/settings/SubscriptionPanel.module.css` içinde `.invoiceRow` kuralını bul:

```css
.invoiceRow {
  display: grid;
  grid-template-columns: 1.6fr 1fr 1fr 0.8fr;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid var(--color-border);
  font-size: 13px;
}
```

ve `grid-template-columns` satırını şununla değiştir (5. kolon "Öde" butonu için):

```css
.invoiceRow {
  display: grid;
  grid-template-columns: 1.4fr 0.9fr 0.9fr 0.8fr 1fr;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid var(--color-border);
  font-size: 13px;
}
```

Dosyanın sonuna ekle:

```css
.paymentSuccessBanner {
  background: var(--color-success-100);
  color: var(--color-success-700);
  border-radius: var(--radius-sm);
  padding: 10px 14px;
  margin-bottom: 16px;
  font-size: 13px;
  font-weight: 600;
}
```

- [ ] **Step 4: Build to verify no type errors**

Run (from `frontend/`): `npx tsc --noEmit`
Expected: derleme hatasız biter (çıktı yok).

- [ ] **Step 5: Visual verification in the browser**

Backend'i (Task 5'teki gibi simüle modda) ve frontend dev server'ı (`npm run dev`) çalıştır. Tenant admin olarak giriş yap, `/ayarlar/abonelik`'e git. ISSUED/OVERDUE bir faturada "Öde" butonu görünmeli. Tıkla — tarayıcı `checkoutFormUrl`'e (simüle callback) yönlenip hemen `/ayarlar/abonelik?odeme=basarili`'ye geri dönmeli, yeşil "Ödemeniz alındı, teşekkürler." banner'ı görünmeli, fatura listesindeki durumu "Ödendi" olmalı. Playwright ile ekran görüntüsü alıp doğrula.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/subscriptionApi.ts frontend/src/pages/settings/SubscriptionPanel.tsx frontend/src/pages/settings/SubscriptionPanel.module.css
git commit -m "feat: add pay-invoice button and payment result banner to SubscriptionPanel"
```

---

### Task 7: Son doğrulama

**Files:** Yok (sadece doğrulama).

**Interfaces:** Yok.

- [ ] **Step 1: Run the full backend test suite**

Run (from `backend/`): `./mvnw -q -o test`
Expected: tüm testler (mevcut + Task 1-4'te eklenenler) PASS, 0 hata.

- [ ] **Step 2: Run the frontend build**

Run (from `frontend/`): `npm run build`
Expected: hatasız tamamlanır.

- [ ] **Step 3: Final review**

`git log --oneline -8` ile bu planın commit'lerini gözden geçir — her task'ın ayrı bir commit olarak göründüğünü doğrula. Değişmeyen dosyaları (bu planla ilgisiz, önceden var olan uncommitted değişiklikler) stage etmediğini `git status --short` ile kontrol et.
