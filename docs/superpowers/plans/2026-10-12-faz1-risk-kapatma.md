# Faz 1 — Hızlı Risk Kapatma Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dış HTTP çağrılarına zaman aşımı, eksik hata handler'ları, uçtan uca requestId ve async MDC taşıma ile en acil operasyonel riskleri kapatmak.

**Architecture:** Hepsi `com.vetos.platform` (modül değil, ortak altyapı) altında küçük, bağımsız değişiklikler. Yeni `platform/http/OutboundHttp` fabrikası zaman aşımlı `RestClient` üretir; üç adapter onu kullanır. `GlobalExceptionHandler` yeni handler'lar kazanır. `RequestIdFilter` id'yi header'dan kabul edip yanıta yazar, `ErrorResponse` onu gövdeye koyar. `AsyncConfig` executor'ı `MdcTaskDecorator` ve graceful shutdown alır.

**Tech Stack:** Spring Boot 3.5 (Spring 6.2), JUnit 5, AssertJ, Mockito, MockMvc standalone, `com.sun.net.httpserver.HttpServer` (mevcut adapter testlerindeki sahte sunucu deseni).

**Spec:** `docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md` (Faz 1 bölümü)

## Global Constraints

- Doğrudan yerel `main`; branch yok; push yalnız kullanıcı isteyince.
- Migration yok. Yeni Maven bağımlılığı yok.
- Kod yorumları ve log mesajları projedeki gibi ASCII Türkçe (ş/ç/ğ yok).
- Hata gövdesi sözleşmesi geriye uyumlu: mevcut alanlar (`errorCode`, `message`, `fieldErrors`, `timestamp`, `path`) aynen kalır, sadece `requestId` eklenir.
- Zaman aşımları: Meta + İleti Merkezi (bildirim ve platform SMS) bağlantı 5 sn / okuma 15 sn; iyzico bağlantı 5 sn / okuma 30 sn.
- Birim testleri DB'ye dokunmaz. Tam suite (`./mvnw test`) çalışan :8080 backend ile ortak geliştirme DB'sini kullanır; bu fazda migration olmadığı için sorun yok, kullanıcının :8080/:5173 süreçlerine dokunma.
- Backend komutları `backend/` dizininde: `./mvnw -q test -Dtest=<Sinif>`.

## Review Focus

1. Cevap vermeyen (bağlantıyı kabul edip hiç yanıt yazmayan) sağlayıcı thread'i okuma sınırından uzun kilitlememeli → Task 1 `OutboundHttpTest.should_failWithinReadTimeout_when_serverNeverResponds`.
2. iyzico'ya ulaşılamazken checkout başlatan kullanıcı 500 değil `PAYMENT_GATEWAY_ERROR` görmeli → Task 1 güncellenen `should_notReturnSimulatedCheckoutFormUrl_when_configured`.
3. Saldırgan `X-Request-Id: abc\nSAHTE LOG SATIRI` veya 10 KB'lık bir değer gönderirse bu loga/yanıta taşınmamalı, yeni UUID üretilmeli → Task 3 `should_ignoreIncomingRequestId_when_unsafe`.
4. Executor kuyruğu dolup `CallerRunsPolicy` görevi HTTP isteğinin kendi thread'inde çalıştırırsa, görev bitince isteğin MDC'si (requestId/tenantId) geri gelmeli → Task 4 `should_restoreCallerMdc_when_runOnCallerThread`.
5. Yeni handler'lar eklenirken beklenmeyen hatalar hâlâ 500 dönmeli ve iç mesajı sızdırmamalı → Task 2 `should_return500WithoutLeakingMessage_when_unexpectedException`.

---

### Task 1: Dış HTTP istemcilerine zaman aşımı

**Files:**
- Create: `backend/src/main/java/com/vetos/platform/http/OutboundHttp.java`
- Create: `backend/src/main/java/com/vetos/platform/http/package-info.java`
- Create: `backend/src/test/java/com/vetos/platform/http/OutboundHttpTest.java`
- Modify: `backend/src/main/java/com/vetos/modules/notification/infrastructure/adapter/NotificationProviderAdapter.java` (`this.restClient = RestClient.create();` satırı, ~89)
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/IletiMerkeziPlatformBillingSmsAdapter.java` (~61)
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/IyzicoPaymentGatewayAdapter.java` (~68 ve `initializeCheckout` içindeki try/catch, ~89-95)
- Modify: `backend/src/test/java/com/vetos/modules/platformadmin/infrastructure/adapter/IyzicoPaymentGatewayAdapterTest.java` (~142-147)

**Interfaces:**
- Produces: `public static RestClient OutboundHttp.restClient(Duration connectTimeout, Duration readTimeout)` — Faz 5'te AI ve FaturaEntegratör adaptörleri de buna taşınacak.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/vetos/platform/http/OutboundHttpTest.java`:

```java
package com.vetos.platform.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundHttpTest {

    private HttpServer stubServer;

    @BeforeEach
    void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/slow", exchange -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        stubServer.createContext("/ok", exchange -> {
            byte[] payload = "merhaba".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        stubServer.start();
    }

    @AfterEach
    void stopStubServer() {
        stubServer.stop(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + stubServer.getAddress().getPort() + path;
    }

    @Test
    void should_failWithinReadTimeout_when_serverNeverResponds() {
        RestClient client = OutboundHttp.restClient(Duration.ofSeconds(1), Duration.ofMillis(300));

        long started = System.nanoTime();
        assertThatThrownBy(() -> client.get().uri(url("/slow")).retrieve().body(String.class))
            .isInstanceOf(ResourceAccessException.class);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(elapsedMillis).isLessThan(2_000);
    }

    @Test
    void should_returnBody_when_serverRespondsInTime() {
        RestClient client = OutboundHttp.restClient(Duration.ofSeconds(1), Duration.ofSeconds(2));

        String body = client.get().uri(url("/ok")).retrieve().body(String.class);

        assertThat(body).isEqualTo("merhaba");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=OutboundHttpTest`
Expected: COMPILATION ERROR — `cannot find symbol: variable OutboundHttp`

- [ ] **Step 3: Write minimal implementation**

`backend/src/main/java/com/vetos/platform/http/package-info.java`:

```java
@org.springframework.modulith.NamedInterface("http")
package com.vetos.platform.http;
```

`backend/src/main/java/com/vetos/platform/http/OutboundHttp.java`:

```java
package com.vetos.platform.http;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Dis servislere giden RestClient'lar icin tek fabrika. RestClient.create()
 * HICBIR zaman asimi koymaz: baglantiyi kabul edip cevap yazmayan bir
 * saglayici (iyzico, Meta, Ileti Merkezi) cagiran thread'i suresiz bekletir --
 * HTTP isteginde kullaniciyi, @Async outbox'ta tum executor havuzunu kilitler.
 * Her adapter kendi baglanti/okuma sinirini acikca vermek zorundadir.
 */
public final class OutboundHttp {

    private OutboundHttp() {
    }

    public static RestClient restClient(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw -q test -Dtest=OutboundHttpTest`
Expected: PASS (2 test)

- [ ] **Step 5: Write the failing iyzico test**

`IyzicoPaymentGatewayAdapterTest.java` içinde mevcut testi değiştir (ulaşılamayan gateway artık ham `RestClientException` değil, kullanıcıya anlamlı domain hatası vermeli):

```java
    /** initializeCheckout de yapilandirilmisken simulasyon URL'i uretmemeli; gateway'e ulasilamazsa 500 yerine PAYMENT_GATEWAY_ERROR. */
    @Test
    void should_notReturnSimulatedCheckoutFormUrl_when_configured() {
        assertThatThrownBy(() -> unreachableConfiguredAdapter.initializeCheckout(
            UUID.randomUUID().toString(), new BigDecimal("500.00"), "Test Klinik", "test@example.com"))
            .isInstanceOf(PaymentGatewayException.class)
            .hasCauseInstanceOf(RestClientException.class);
    }
```

`should_notShortCircuitToSimulatedSuccess_when_configuredAndTokenHasSimulatedPrefix` testine DOKUNMA — `retrieveCheckoutResult` bilerek fırlatmaya devam ediyor; `PublicPaymentCallbackController.handleAndRedirect` bunu yakalayıp başarısız yönlendirme yapıyor.

- [ ] **Step 6: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=IyzicoPaymentGatewayAdapterTest`
Expected: FAIL — `should_notReturnSimulatedCheckoutFormUrl_when_configured`: `Expecting actual throwable to be an instance of: PaymentGatewayException but was: ResourceAccessException`

- [ ] **Step 7: Implement adapter changes**

`IyzicoPaymentGatewayAdapter.java`:
- Import ekle: `import com.vetos.platform.http.OutboundHttp;`, `import org.springframework.web.client.RestClientException;`, `import java.time.Duration;`
- Sınıf sabitlerine ekle:

```java
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
```

- Kurucuda `this.restClient = RestClient.create();` → `this.restClient = OutboundHttp.restClient(CONNECT_TIMEOUT, READ_TIMEOUT);`
- `initializeCheckout` içindeki try/catch'i şu hale getir:

```java
        Map<String, Object> response;
        try {
            response = postSigned(INITIALIZE_PATH, requestBody);
        } catch (RestClientResponseException e) {
            log.warn("iyzico checkout baslatilamadi: durum={}, govde={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentGatewayException("iyzico checkout baslatilamadi", e);
        } catch (RestClientException e) {
            // Baglanti hatasi / zaman asimi -- yakalanmazsa GlobalExceptionHandler'da
            // 500 INTERNAL_ERROR'a dusuyordu; kullanici "tekrar deneyin" gormeli.
            log.warn("iyzico'ya ulasilamadi: {}", e.getMessage());
            throw new PaymentGatewayException("iyzico'ya ulasilamadi", e);
        }
```

(`RestClientResponseException`, `RestClientException`'ın alt sınıfı olduğundan sıra önemli: önce özel olan.)

`NotificationProviderAdapter.java`:
- Import: `import com.vetos.platform.http.OutboundHttp;`, `import java.time.Duration;` (zaten varsa ekleme)
- Sabitler:

```java
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
```

- Paket-özel kurucuda `this.restClient = RestClient.create();` → `this.restClient = OutboundHttp.restClient(CONNECT_TIMEOUT, READ_TIMEOUT);`

`IletiMerkeziPlatformBillingSmsAdapter.java`: aynı iki sabit (5 sn / 15 sn), aynı importlar, paket-özel kurucuda aynı değişiklik.

Her üç dosyada `RestClient` importu `restClient` alanının tipi için kalmaya devam eder.

- [ ] **Step 8: Run adapter tests to verify they pass**

Run: `cd backend && ./mvnw -q test -Dtest='OutboundHttpTest,IyzicoPaymentGatewayAdapterTest,NotificationProviderAdapterTest,IletiMerkeziPlatformBillingSmsAdapterTest'`
Expected: PASS (tümü)

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/vetos/platform/http backend/src/test/java/com/vetos/platform/http \
  backend/src/main/java/com/vetos/modules/notification/infrastructure/adapter/NotificationProviderAdapter.java \
  backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/IletiMerkeziPlatformBillingSmsAdapter.java \
  backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/IyzicoPaymentGatewayAdapter.java \
  backend/src/test/java/com/vetos/modules/platformadmin/infrastructure/adapter/IyzicoPaymentGatewayAdapterTest.java
git commit -m "fix(platform): dis HTTP istemcilerine zaman asimi; iyzico'ya ulasilamazsa PAYMENT_GATEWAY_ERROR"
```

---

### Task 2: Eksik istemci hatası handler'ları

**Files:**
- Modify: `backend/src/main/java/com/vetos/platform/web/GlobalExceptionHandler.java`
- Create: `backend/src/test/java/com/vetos/platform/web/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `ErrorResponse.of(String errorCode, String message, String path)` (mevcut)
- Produces: yeni `errorCode`'lar — `MISSING_PARAMETER` (400), `INVALID_PARAMETER` (400), `METHOD_NOT_ALLOWED` (405, `Allow` header'lı), `UNSUPPORTED_MEDIA_TYPE` (415), `PAYLOAD_TOO_LARGE` (413), `NOT_FOUND` (404).

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/vetos/platform/web/GlobalExceptionHandlerTest.java`:

```java
package com.vetos.platform.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class ProbeController {

        @GetMapping("/probe/param")
        String requiredParam(@RequestParam("q") String q) {
            return q;
        }

        @GetMapping("/probe/typed/{id}")
        String typed(@PathVariable("id") UUID id) {
            return id.toString();
        }

        @PostMapping(value = "/probe/json", consumes = MediaType.APPLICATION_JSON_VALUE)
        String json(@RequestBody Map<String, Object> body) {
            return "ok";
        }

        @GetMapping("/probe/too-large")
        String tooLarge() {
            throw new MaxUploadSizeExceededException(15L * 1024 * 1024);
        }

        @GetMapping("/probe/no-resource")
        String noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "probe/no-resource");
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("gizli ic detay: db sifresi");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void should_return400_when_requiredQueryParamMissing() throws Exception {
        mockMvc.perform(get("/probe/param"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"))
            .andExpect(jsonPath("$.path").value("/probe/param"));
    }

    @Test
    void should_return400_when_pathVariableHasWrongType() throws Exception {
        mockMvc.perform(get("/probe/typed/uuid-degil"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void should_return405WithAllowHeader_when_methodNotSupported() throws Exception {
        mockMvc.perform(delete("/probe/param"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", containsString("GET")))
            .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void should_return415_when_contentTypeNotSupported() throws Exception {
        mockMvc.perform(post("/probe/json").contentType(MediaType.TEXT_PLAIN).content("x"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void should_return413_when_uploadTooLarge() throws Exception {
        mockMvc.perform(get("/probe/too-large"))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.errorCode").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void should_return404_when_noResourceFound() throws Exception {
        mockMvc.perform(get("/probe/no-resource"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void should_return500WithoutLeakingMessage_when_unexpectedException() throws Exception {
        mockMvc.perform(get("/probe/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value(not(containsString("gizli"))));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=GlobalExceptionHandlerTest`
Expected: FAIL — 6 test 500/`INTERNAL_ERROR` aldığı için kırmızı (`should_return500WithoutLeakingMessage_when_unexpectedException` zaten yeşil; mevcut davranışı koruduğunu kanıtlar).

- [ ] **Step 3: Write minimal implementation**

`GlobalExceptionHandler.java` — importlar:

```java
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;
```

`handleNotReadable` metodundan SONRA, `handleUnexpected`'tan ÖNCE ekle:

```java
    /**
     * Asagidaki handler'lar olmadan bu istemci hatalarinin hepsi genel Exception
     * handler'ina dusup 500 + ERROR log uretiyordu -- hem api-conventions.md'deki
     * 4xx sozlesmesini bozuyor hem de gercek sunucu hatalarini loglarda gomuyordu.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("MISSING_PARAMETER", "Zorunlu parametre eksik: " + ex.getParameterName(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("INVALID_PARAMETER", "Gecersiz parametre degeri: " + ex.getName(), request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            builder.allow(supported.toArray(HttpMethod[]::new));
        }
        return builder.body(ErrorResponse.of("METHOD_NOT_ALLOWED", "Bu uc nokta " + ex.getMethod() + " istegini desteklemiyor", request.getRequestURI()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(ErrorResponse.of("UNSUPPORTED_MEDIA_TYPE", "Desteklenmeyen icerik tipi", request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ErrorResponse.of("PAYLOAD_TOO_LARGE", "Dosya boyutu siniri asildi", request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("NOT_FOUND", "Istenen kaynak bulunamadi", request.getRequestURI()));
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw -q test -Dtest=GlobalExceptionHandlerTest`
Expected: PASS (7 test)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/platform/web/GlobalExceptionHandler.java backend/src/test/java/com/vetos/platform/web/GlobalExceptionHandlerTest.java
git commit -m "fix(platform): eksik parametre/tip, 405, 415, 413 ve 404 hatalari 500 yerine dogru 4xx doner"
```

---

### Task 3: requestId uçtan uca (header + hata gövdesi)

**Files:**
- Modify: `backend/src/main/java/com/vetos/platform/web/RequestIdFilter.java`
- Modify: `backend/src/main/java/com/vetos/platform/web/ErrorResponse.java`
- Modify: `backend/src/main/java/com/vetos/platform/security/SecurityConfig.java:101`
- Modify: `backend/src/test/java/com/vetos/platform/web/RequestIdFilterTest.java`
- Create: `backend/src/test/java/com/vetos/platform/web/ErrorResponseTest.java`
- Modify: `backend/src/test/java/com/vetos/platform/security/SecurityConfigCorsTest.java`

**Interfaces:**
- Consumes: MDC anahtarı `"requestId"` (mevcut, `RequestIdFilter`).
- Produces: `public static final String RequestIdFilter.HEADER = "X-Request-Id"`; `ErrorResponse` record'unun yeni son bileşeni `String requestId` (factory'ler MDC'den doldurur; `new ErrorResponse(...)` kodda başka yerde kullanılmıyor).

- [ ] **Step 1: Write the failing tests**

`RequestIdFilterTest.java` — importlara `import static org.mockito.Mockito.verify;` ve `import static org.mockito.Mockito.when;` ekle, sınıfın sonuna:

```java
    @Test
    void should_reuseIncomingRequestId_when_safe() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-Request-Id")).thenReturn("frontend-1234abcd");
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(MDC.get("requestId")));

        assertThat(seen.get()).isEqualTo("frontend-1234abcd");
        verify(response).setHeader("X-Request-Id", "frontend-1234abcd");
    }

    @Test
    void should_ignoreIncomingRequestId_when_unsafe() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<String> seen = new AtomicReference<>();

        for (String unsafe : new String[]{"abc12345\nSAHTE LOG SATIRI", "x".repeat(10_000), "kisa"}) {
            when(request.getHeader("X-Request-Id")).thenReturn(unsafe);
            filter.doFilter(request, response, (req, res) -> seen.set(MDC.get("requestId")));
            assertThat(seen.get()).isNotEqualTo(unsafe).matches("[0-9a-f-]{36}");
        }
    }

    @Test
    void should_writeGeneratedRequestIdToResponseHeader() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(MDC.get("requestId")));

        verify(response).setHeader("X-Request-Id", seen.get());
    }
```

`backend/src/test/java/com/vetos/platform/web/ErrorResponseTest.java`:

```java
package com.vetos.platform.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void should_carryRequestIdFromMdc() {
        MDC.put("requestId", "req-42-abcdef");

        assertThat(ErrorResponse.of("X", "m", "/p").requestId()).isEqualTo("req-42-abcdef");
        assertThat(ErrorResponse.ofValidation("m", List.of(), "/p").requestId()).isEqualTo("req-42-abcdef");
    }

    @Test
    void should_haveNullRequestId_when_mdcEmpty() {
        assertThat(ErrorResponse.of("X", "m", "/p").requestId()).isNull();
    }
}
```

`SecurityConfigCorsTest.java` — sınıfın sonuna:

```java
    @Test
    void should_exposeRequestIdHeader_soFrontendCanReportIt() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        CorsConfigurationSource source = aSecurityConfig().corsConfigurationSource(environment);

        assertThat(resolveConfig(source).getExposedHeaders()).contains("Location", "X-Request-Id");
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q test -Dtest='RequestIdFilterTest,ErrorResponseTest,SecurityConfigCorsTest'`
Expected: COMPILATION ERROR — `cannot find symbol: method requestId()` (ErrorResponseTest). Bu derleme hatası giderilince RequestIdFilter ve CORS testleri de kırmızı kalmalı.

- [ ] **Step 3: Write minimal implementation**

`RequestIdFilter.java` tamamı:

```java
package com.vetos.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Guvenlik zincirinin EN BASINDA calisir (kimlik dogrulamadan once) --
 * kimlik dogrulanmamis istekler (basarisiz login denemeleri, herkese acik
 * uc noktalar, rate-limit'e takilan istekler) de dahil HER istek bir
 * requestId alir. JwtAuthenticationFilter (bu filtreden SONRA calisir)
 * kimlik dogrulanirsa ayrica tenantId'yi MDC'ye ekler.
 *
 * Istemci (frontend, eklenti, ters proxy) X-Request-Id gonderirse ayni id
 * kullanilir -- loglar uctan uca eslesir. Ama header istemci kontrolunde:
 * log enjeksiyonu (satir sonu) ve sinirsiz uzunluga karsi sadece guvenli
 * bicim kabul edilir, aksi halde yeni UUID uretilir. Id her yanita
 * X-Request-Id header'i olarak yazilir (hata govdesinde de requestId var).
 */
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches()
            ? incoming
            : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```

`ErrorResponse.java` tamamı:

```java
package com.vetos.platform.web;

import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;

/**
 * requestId, RequestIdFilter'in MDC'ye koydugu id'dir -- kullanicinin
 * gordugu hatayi sunucu loguyla eslestirmek icin. Filtre disinda (orn.
 * zamanlanmis is) uretilirse null.
 */
public record ErrorResponse(
    String errorCode,
    String message,
    List<FieldErrorItem> fieldErrors,
    Instant timestamp,
    String path,
    String requestId
) {

    public static ErrorResponse of(String errorCode, String message, String path) {
        return new ErrorResponse(errorCode, message, null, Instant.now(), path, MDC.get("requestId"));
    }

    public static ErrorResponse ofValidation(String message, List<FieldErrorItem> fieldErrors, String path) {
        return new ErrorResponse("VALIDATION_FAILED", message, fieldErrors, Instant.now(), path, MDC.get("requestId"));
    }

    public record FieldErrorItem(String field, String message) {}
}
```

`SecurityConfig.java:101`:

```java
        // X-Request-Id: kullanici hata bildirirken frontend bu id'yi gosterebilsin diye.
        configuration.setExposedHeaders(List.of("Location", "X-Request-Id"));
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q test -Dtest='RequestIdFilterTest,ErrorResponseTest,SecurityConfigCorsTest,GlobalExceptionHandlerTest'`
Expected: PASS (tümü)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/platform/web/RequestIdFilter.java backend/src/main/java/com/vetos/platform/web/ErrorResponse.java \
  backend/src/main/java/com/vetos/platform/security/SecurityConfig.java \
  backend/src/test/java/com/vetos/platform/web/RequestIdFilterTest.java backend/src/test/java/com/vetos/platform/web/ErrorResponseTest.java \
  backend/src/test/java/com/vetos/platform/security/SecurityConfigCorsTest.java
git commit -m "feat(platform): X-Request-Id yanit header'i ve hata govdesinde requestId"
```

---

### Task 4: Async MDC taşıma + graceful shutdown

**Files:**
- Create: `backend/src/main/java/com/vetos/platform/config/MdcTaskDecorator.java`
- Create: `backend/src/test/java/com/vetos/platform/config/MdcTaskDecoratorTest.java`
- Modify: `backend/src/main/java/com/vetos/platform/config/AsyncConfig.java`
- Modify: `backend/src/main/resources/application.yml` (`server:` bloğu ve `spring:` bloğu)

**Interfaces:**
- Consumes: `taskExecutor` bean'i (mevcut, `AsyncConfig`).
- Produces: `class MdcTaskDecorator implements org.springframework.core.task.TaskDecorator` (paket-özel).

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/vetos/platform/config/MdcTaskDecoratorTest.java`:

```java
package com.vetos.platform.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void should_propagateSubmittersMdc_toWorkerThread() throws InterruptedException {
        MDC.put("requestId", "req-A");
        MDC.put("tenantId", "tenant-1");
        AtomicReference<String> seenRequestId = new AtomicReference<>();
        AtomicReference<String> seenTenantId = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> {
            seenRequestId.set(MDC.get("requestId"));
            seenTenantId.set(MDC.get("tenantId"));
        });

        Thread worker = new Thread(decorated);
        worker.start();
        worker.join();

        assertThat(seenRequestId.get()).isEqualTo("req-A");
        assertThat(seenTenantId.get()).isEqualTo("tenant-1");
    }

    @Test
    void should_notLeakStaleMdc_when_submitterHadNone() throws InterruptedException {
        AtomicReference<String> seenDuring = new AtomicReference<>("baslangic");
        AtomicReference<String> seenAfter = new AtomicReference<>("baslangic");
        // Gonderen (test thread'i) MDC'siz -- decorate BURADA, worker'dan once cagrilmali.
        Runnable decorated = decorator.decorate(() -> seenDuring.set(MDC.get("requestId")));

        Thread pooledWorker = new Thread(() -> {
            MDC.put("requestId", "onceki-gorevden-kalan");
            decorated.run();
            seenAfter.set(MDC.get("requestId"));
        });
        pooledWorker.start();
        pooledWorker.join();

        assertThat(seenDuring.get()).isNull();
        assertThat(seenAfter.get()).isEqualTo("onceki-gorevden-kalan");
    }

    @Test
    void should_restoreCallerMdc_when_runOnCallerThread() {
        MDC.put("requestId", "req-A");
        Runnable decorated = decorator.decorate(() -> MDC.put("requestId", "gorev-icinde-degisti"));

        MDC.put("requestId", "req-B");
        decorated.run();

        assertThat(MDC.get("requestId")).isEqualTo("req-B");
    }
}
```

(`should_notLeakStaleMdc_when_submitterHadNone`: `decorate` test thread'inde, MDC boşken çağrılıyor (`@AfterEach` önceki testleri temizler); worker thread'inin kalıntı değeri görevin içinde görünmemeli, görev bitince worker'ın kendi değeri geri gelmeli.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=MdcTaskDecoratorTest`
Expected: COMPILATION ERROR — `cannot find symbol: class MdcTaskDecorator`

- [ ] **Step 3: Write minimal implementation**

`backend/src/main/java/com/vetos/platform/config/MdcTaskDecorator.java`:

```java
package com.vetos.platform.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * @Async isler HTTP isteginin thread'inden farkli bir thread'de calisir; MDC
 * thread-local oldugu icin requestId/tenantId kaybolur ve outbox loglari
 * (bildirim, e-fatura, TARBIL) tetikleyen istekle eslestirilemez. Gorev
 * gonderilirken MDC kopyalanir, calisirken kurulur, bitince calisan thread'in
 * ONCEKI MDC'si geri yuklenir -- CallerRunsPolicy gorevi istegin kendi
 * thread'inde calistirdiginda istegin MDC'si bozulmasin, havuz thread'i de
 * bir sonraki goreve eski deger tasimasin diye.
 *
 * Sadece MDC tasinir; TenantContext bilerek TASINMAZ -- executor'lar kiraciyi
 * outbox kaydindan acikca kurar (bkz. EInvoiceSubmissionExecutor "kopruleme kurali").
 */
class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> submitterContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            setOrClear(submitterContext);
            try {
                runnable.run();
            } finally {
                setOrClear(previousContext);
            }
        };
    }

    private static void setOrClear(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
```

`AsyncConfig.java` — `executor.setRejectedExecutionHandler(...)` satırından sonra, `executor.initialize();` öncesine:

```java
        executor.setTaskDecorator(new MdcTaskDecorator());
        // Kapanista (deploy/restart) kuyruktaki outbox gonderimleri yarida
        // kesilmesin; kalanlari zaten retry zamanlayicilari toplar, ama yarim
        // kalmis bir dis cagri "gonderildi mi?" belirsizligi yaratir.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
```

Javadoc'a bir cümle ekle: `MdcTaskDecorator ile requestId/tenantId log korelasyonu async thread'e tasinir.`

`application.yml`:
- `spring:` bloğunun altına (ör. `task:` bloğundan sonra, aynı girinti):

```yaml
  lifecycle:
    # server.shutdown=graceful ile: devam eden HTTP istekleri ve async gorevler
    # icin en fazla bu kadar beklenir (VPS'teki systemd TimeoutStopSec'ten kisa olmali).
    timeout-per-shutdown-phase: 30s
```

- `server:` bloğuna `port:` satırının altına:

```yaml
  shutdown: graceful
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw -q test -Dtest=MdcTaskDecoratorTest`
Expected: PASS (3 test)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/platform/config backend/src/test/java/com/vetos/platform/config backend/src/main/resources/application.yml
git commit -m "feat(platform): async gorevlere MDC tasima ve graceful shutdown"
```

---

### Task 5: Faz doğrulaması

**Files:** yok (yalnız doğrulama).

- [ ] **Step 1: Tam test suite**

Run: `cd backend && ./mvnw test`
Expected: `BUILD SUCCESS`, 0 failure. (Ortak geliştirme DB'sini kullanır; migration yok.) Kırmızı varsa hangi testin ve neden kırıldığını raporla — özellikle `ErrorResponse`'a yeni alan eklenmesi JSON'u birebir karşılaştıran entegrasyon testlerini etkileyebilir; öyleyse beklentiye `requestId`'yi ekle, alanı geri alma.

- [ ] **Step 2: Elle kontrol listesini kullanıcıya bırak**

Kullanıcı :8080 backend'ini yeniden başlattıktan sonra:
1. `curl -i http://localhost:8080/api/v1/patients/uuid-degil -H "Authorization: Bearer <token>"` → `400`, gövdede `"errorCode":"INVALID_PARAMETER"`, gövdedeki `requestId` = `X-Request-Id` header'ı.
2. `curl -i -H "X-Request-Id: test-istek-0001" http://localhost:8080/actuator/health` → yanıtta `X-Request-Id: test-istek-0001`.
3. Backend loglarında bir bildirim gönderiminin (`async-outbox-` thread'i) satırında `[<requestId>/<tenantId>]` dolu görünür.

- [ ] **Step 3: Yol haritasını güncelle**

`docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md` → Faz 1 başlığındaki `← **ŞİMDİ**` işaretini `✅ tamamlandı (<tarih>)` yap, Faz 2'ye `← **SIRADAKİ** (önce brainstorming)` ekle.

```bash
git add docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md
git commit -m "docs: yol haritasinda faz 1 tamamlandi"
```
