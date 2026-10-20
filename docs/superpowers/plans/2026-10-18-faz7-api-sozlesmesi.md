# Faz 7 — API Sözleşmesi ve Versiyonlama Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chrome eklentisine zorunlu güncelleme (426), Vetly'den indirme/sürüm kartı, yazılı versiyonlama kuralları ve yalnızca geliştirmede açık OpenAPI sayfası.

**Architecture:**
- **Backend:**
  - Eklenti güvenlik zincirine (`/api/v1/tarbil-extension/**`) bir `ClientVersionFilter` eklenir. Bu filtre `X-Client-Version` başlığını `vetly.extension.min-version` ile karşılaştırır ve eskiyse standart `ErrorResponse` ile 426 döner.
  - springdoc ana zincirde yalnızca `springdoc.api-docs.enabled=true` iken serbesttir. Prod'da bu ayar kapalıdır.
- **Eklenti (0.2.0):** Sürümü `package.json`'dan okur, her isteğe başlığı ekler, 426'yı `UPGRADE_REQUIRED` hatasına çevirir ve kendini zip'leyip web uygulamasının `public/downloads/` klasörüne koyar.
- **Web uygulaması:** Ayarlar > Entegrasyonlar'daki TARBİL kartı, PING ile yüklü sürümü ve json ile en yeni sürümü okuyup karşılaştırır.

**Tech Stack:**
- Spring Boot 3.5.16 (Spring Security 6), Java 21, springdoc-openapi 2.8.17.
- Eklenti: Vite 5 + vitest 2 + React 18 (MV3), fflate 0.8.2.
- Web uygulaması: React 18 + Vite 5; vitest 2 + jsdom 25 bu planla eklenir.

**Spec:** `docs/superpowers/specs/2026-10-18-api-sozlesmesi-faz7-design.md`

## Global Constraints

- **Commit YOK, push YOK:** Değişiklikler çalışma ağacında kalır (IntelliJ changelist). Hiçbir adım `git add`/`git commit` çalıştırmaz.
- **Branch açılmaz:** `main` üzerinde çalışılır.
- **426 mesajı (birebir):** `Vetly TARBİL eklentinizin sürümü artık desteklenmiyor. Vetly'de Ayarlar > Entegrasyonlar bölümünden yeni sürümü indirin.`
- **Hata kodları:**
  - backend `errorCode`: `CLIENT_UPGRADE_REQUIRED`;
  - eklenti `ErrorCode`: `UPGRADE_REQUIRED`.
- **Başlıklar:**
  - istek: `X-Client-Version`;
  - cevap: `X-Min-Client-Version`.
- **Ayar:** `vetly.extension.min-version`, ortam değişkeni `VETLY_EXTENSION_MIN_VERSION`, varsayılan `0.1.0`.
- **Sürüm kuralı:**
  - `x.y.z`, sayısal karşılaştırma;
  - başlık yoksa `0.1.0` sayılır;
  - bozuk başlık `0.0.0` sayılır;
  - baştaki/sondaki boşluk kırpılır.
- **Eklenti sürümü:** `0.2.0`.
- **İndirme dosyaları:**
  - `frontend/public/downloads/vetly-tarbil-eklenti.zip`
  - `frontend/public/downloads/vetly-tarbil-eklenti.json` (`{"version":"0.2.0"}`)
- **springdoc:** `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17`; adreslere anotasyon eklenmez.
- **Testler:**
  - Backend `@SpringBootTest` testleri canlı docker Postgres (5433) ister. Docker kapalıysa `backend/` içinde `docker compose up -d`.
  - Backend: `cd backend && ./mvnw test -Dtest=<Sınıf>`.
  - Eklenti: `cd extension && npm test`.
  - Web uygulaması: `cd frontend && npm test` (Task 5 ekler).
- **Dil:** Kullanıcıya görünen metinler Türkçe. Java yorumları mevcut dosyalardaki gibi ASCII Türkçe (ı→i, ş→s).

## Review Focus

1. **Sürüm sıralaması:** `0.10.0` > `0.9.0` olmalı. Metin karşılaştırması bunu tersine çevirir. Testler: Task 1 `ClientVersionTest`, Task 5 `version.test.ts`.
2. **Boşluklu başlık:** `X-Client-Version: " 0.2.0 "` kabul edilmeli. Test: Task 1 filtre testi.
3. **426 gövdesi JSON değilse** (ör. Nginx hata sayfası): eklenti sabit 426 mesajını göstermeli, "Beklenmeyen yanıt" değil. Test: Task 3.
4. **426'da token korunmalı:** Güncelleme sonrası yeniden eşleştirme gerekmemeli. Test: Task 3.
5. **Sürüm dosyası yerine HTML gelirse:** Nginx/SPA eksik json için `index.html` döndürebilir. Kart çökmeden "Eklenti dosyası şu an hazır değil" göstermeli. Test: Task 5 `extensionRelease.test.ts`.
6. **Filtre sırası:** 426 cevabında `requestId` dolu olmalı, yani RequestIdFilter'dan sonra çalışmalı. Test: Task 1 entegrasyon testi.

---

### Task 1: Backend — eklenti sürüm kontrolü (426)

**Files:**
- Create: `backend/src/main/java/com/vetos/platform/security/ClientVersion.java`
- Create: `backend/src/main/java/com/vetos/platform/security/ClientVersionFilter.java`
- Modify: `backend/src/main/java/com/vetos/platform/security/TarbilExtensionSecurityConfig.java`
- Modify: `backend/src/main/resources/application.yml` (`vetly:` bloğu, `quota:` altından sonra)
- Test: `backend/src/test/java/com/vetos/platform/security/ClientVersionTest.java`
- Test: `backend/src/test/java/com/vetos/platform/security/ClientVersionFilterTest.java`
- Test: `backend/src/test/java/com/vetos/ClientVersionEnforcementIntegrationTest.java`

**Interfaces:**
- Produces:
  - `ClientVersion` (public record `int major, int minor, int patch`, `Comparable<ClientVersion>`);
  - `ClientVersion.parse(String) → ClientVersion`, bozuksa `IllegalArgumentException`;
  - `ClientVersion.tryParse(String) → Optional<ClientVersion>`;
  - `ClientVersion.ZERO`, `ClientVersion.WHEN_MISSING` (0.1.0);
  - `ClientVersionFilter(ClientVersion minVersion, ObjectMapper objectMapper)`;
  - sabitler `ClientVersionFilter.HEADER = "X-Client-Version"`, `MIN_HEADER = "X-Min-Client-Version"`, `ERROR_CODE = "CLIENT_UPGRADE_REQUIRED"`, `MESSAGE`.
- Consumes: `com.vetos.platform.web.ErrorResponse.of(String errorCode, String message, String path)` (mevcut; `requestId`'yi MDC'den alır).

- [ ] **Step 1: `ClientVersion` için başarısız testi yaz**

```java
package com.vetos.platform.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientVersionTest {

    @Test
    void should_parseThreeNumbers() {
        assertThat(ClientVersion.parse("0.2.0")).isEqualTo(new ClientVersion(0, 2, 0));
        assertThat(ClientVersion.parse(" 1.10.3 ")).isEqualTo(new ClientVersion(1, 10, 3));
    }

    @Test
    void should_compareNumerically_notAsText() {
        assertThat(ClientVersion.parse("0.10.0")).isGreaterThan(ClientVersion.parse("0.9.0"));
        assertThat(ClientVersion.parse("1.0.0")).isGreaterThan(ClientVersion.parse("0.99.99"));
        assertThat(ClientVersion.parse("0.2.0")).isEqualByComparingTo(ClientVersion.parse("0.2.0"));
        assertThat(ClientVersion.parse("0.1.9")).isLessThan(ClientVersion.parse("0.2.0"));
    }

    @Test
    void should_rejectMalformed() {
        for (String bad : new String[] {"", "abc", "1.2", "1.2.3.4", "-1.0.0", "1.x.0", "v1.0.0", "1..0"}) {
            assertThat(ClientVersion.tryParse(bad)).as(bad).isEmpty();
        }
        assertThat(ClientVersion.tryParse(null)).isEmpty();
        assertThatThrownBy(() -> ClientVersion.parse("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_exposeDefaults() {
        assertThat(ClientVersion.ZERO).isEqualTo(new ClientVersion(0, 0, 0));
        assertThat(ClientVersion.WHEN_MISSING).isEqualTo(new ClientVersion(0, 1, 0));
    }
}
```

- [ ] **Step 2: Testin derleme hatasıyla düştüğünü gör**

Run: `cd backend && ./mvnw test -Dtest=ClientVersionTest`
Expected: FAIL (`ClientVersion` sınıfı yok).

- [ ] **Step 3: `ClientVersion` yaz**

```java
package com.vetos.platform.security;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Eklenti surumu (x.y.z), sayisal karsilastirma -- spec 2026-10-18 Faz 7 §1.
 * "0.10.0" > "0.9.0"; metin karsilastirmasi bunu ters cevirirdi.
 */
public record ClientVersion(int major, int minor, int patch) implements Comparable<ClientVersion> {

    public static final ClientVersion ZERO = new ClientVersion(0, 0, 0);
    /** X-Client-Version gondermeyen (0.1.0) eklentiler bu surum sayilir. */
    public static final ClientVersion WHEN_MISSING = new ClientVersion(0, 1, 0);

    private static final Pattern FORMAT = Pattern.compile("(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})");
    private static final Comparator<ClientVersion> ORDER = Comparator
        .comparingInt(ClientVersion::major)
        .thenComparingInt(ClientVersion::minor)
        .thenComparingInt(ClientVersion::patch);

    public static Optional<ClientVersion> tryParse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher m = FORMAT.matcher(raw.strip());
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new ClientVersion(
            Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
    }

    public static ClientVersion parse(String raw) {
        return tryParse(raw).orElseThrow(() -> new IllegalArgumentException("Gecersiz surum (x.y.z bekleniyor): " + raw));
    }

    @Override
    public int compareTo(ClientVersion other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
```

- [ ] **Step 4: Testin geçtiğini gör**

Run: `cd backend && ./mvnw test -Dtest=ClientVersionTest`
Expected: PASS (4 test).

- [ ] **Step 5: Filtre için başarısız birim testini yaz**

```java
package com.vetos.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ClientVersionFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private MockHttpServletResponse run(String min, String header) throws Exception {
        ClientVersionFilter filter = new ClientVersionFilter(ClientVersion.parse(min), objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tarbil-extension/me");
        if (header != null) {
            request.addHeader(ClientVersionFilter.HEADER, header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        if (response.getStatus() == 200) {
            assertThat(chain.getRequest()).as("istek zincire gecmeli").isNotNull();
        } else {
            assertThat(chain.getRequest()).as("istek zincire gecmemeli").isNull();
        }
        return response;
    }

    @Test
    void should_reject_when_versionOlderThanMin() throws Exception {
        MockHttpServletResponse response = run("0.2.0", "0.1.9");

        assertThat(response.getStatus()).isEqualTo(426);
        assertThat(response.getHeader(ClientVersionFilter.MIN_HEADER)).isEqualTo("0.2.0");
        assertThat(response.getContentType()).startsWith("application/json");
        String body = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("\"errorCode\":\"CLIENT_UPGRADE_REQUIRED\"");
        assertThat(body).contains("Ayarlar > Entegrasyonlar");
        assertThat(body).contains("\"path\":\"/api/v1/tarbil-extension/me\"");
    }

    @Test
    void should_pass_when_versionEqualOrNewer() throws Exception {
        assertThat(run("0.2.0", "0.2.0").getStatus()).isEqualTo(200);
        assertThat(run("0.2.0", "0.10.0").getStatus()).isEqualTo(200);
        assertThat(run("0.2.0", " 0.2.0 ").getStatus()).isEqualTo(200);
    }

    @Test
    void should_treatMissingHeaderAs010() throws Exception {
        assertThat(run("0.1.0", null).getStatus()).isEqualTo(200);
        assertThat(run("0.2.0", null).getStatus()).isEqualTo(426);
        assertThat(run("0.2.0", "  ").getStatus()).isEqualTo(426);
    }

    @Test
    void should_treatMalformedAsOldest() throws Exception {
        assertThat(run("0.1.0", "abc").getStatus()).isEqualTo(426);
        assertThat(run("0.0.0", "abc").getStatus()).isEqualTo(200);
    }
}
```

- [ ] **Step 6: Testin derleme hatasıyla düştüğünü gör**

Run: `cd backend && ./mvnw test -Dtest=ClientVersionFilterTest`
Expected: FAIL (`ClientVersionFilter` yok).

- [ ] **Step 7: `ClientVersionFilter` yaz**

```java
package com.vetos.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.platform.web.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Eklenti zinciri (TarbilExtensionSecurityConfig) icin zorunlu guncelleme -- spec 2026-10-18 Faz 7 §1.
 * X-Client-Version yoksa 0.1.0, bozuksa 0.0.0 sayilir; min-version'dan eskiyse 426 + ErrorResponse.
 * Bean DEGIL (new ile zincire eklenir) -- aksi halde Boot bunu tum isteklere servlet filtresi yapar.
 */
public class ClientVersionFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Client-Version";
    public static final String MIN_HEADER = "X-Min-Client-Version";
    public static final String ERROR_CODE = "CLIENT_UPGRADE_REQUIRED";
    public static final String MESSAGE =
        "Vetly TARBİL eklentinizin sürümü artık desteklenmiyor. Vetly'de Ayarlar > Entegrasyonlar bölümünden yeni sürümü indirin.";

    private final ClientVersion minVersion;
    private final ObjectMapper objectMapper;

    public ClientVersionFilter(ClientVersion minVersion, ObjectMapper objectMapper) {
        this.minVersion = minVersion;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (clientVersion(request.getHeader(HEADER)).compareTo(minVersion) >= 0) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.UPGRADE_REQUIRED.value());
        response.setHeader(MIN_HEADER, minVersion.toString());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        var body = ErrorResponse.of(ERROR_CODE, MESSAGE, request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static ClientVersion clientVersion(String header) {
        if (header == null || header.isBlank()) {
            return ClientVersion.WHEN_MISSING;
        }
        return ClientVersion.tryParse(header).orElse(ClientVersion.ZERO);
    }
}
```

Not: `MockHttpServletResponse.getContentType()` karakter setiyle birlikte `application/json;charset=UTF-8` döner; test `startsWith` kullanır.

- [ ] **Step 8: Filtre testinin geçtiğini gör**

Run: `cd backend && ./mvnw test -Dtest=ClientVersionFilterTest`
Expected: PASS (4 test).

- [ ] **Step 9: Ayarı ve zincir bağlantısını ekle**

`application.yml` içinde `vetly:` bloğunda `quota:` bölümünden sonra (aynı girintiyle):

```yaml
  # Faz 7 (spec 2026-10-18): TARBIL eklentisi icin en dusuk surum. Bundan eski eklenti 426 alir.
  # Yukseltmeden ONCE yeni surum Vetly'den indirilebilir olmali (docs/api-conventions.md Versiyonlama).
  extension:
    min-version: ${VETLY_EXTENSION_MIN_VERSION:0.1.0}
```

`TarbilExtensionSecurityConfig.java`:
- import ekle: `com.fasterxml.jackson.databind.ObjectMapper`, `org.springframework.beans.factory.annotation.Value`.
- final alan ekle: `private final ObjectMapper objectMapper;`
- Bean metodunun imzasını şöyle değiştir:

```java
    public SecurityFilterChain tarbilExtensionSecurityFilterChain(
        HttpSecurity http,
        TenantRateLimiter tenantRateLimiter,
        @Value("${vetly.extension.min-version:0.1.0}") String minVersion
    ) throws Exception {
```

- Filtre kayıtlarını şu hale getir. `ClientVersionFilter` satırı `RateLimitFilter` kaydından SONRA gelmeli. Neden: iki filtre de `ExtensionTokenAuthenticationFilter`'ın hemen önüne konur ve aynı sırayı paylaşan filtreler kayıt sırasıyla dizilir. Böylece çalışma sırası RequestId → RateLimit → ClientVersion → ExtensionToken olur.

```java
            .addFilterBefore(new ExtensionTokenAuthenticationFilter(extensionTokenAuthenticator), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new RateLimitFilter(), ExtensionTokenAuthenticationFilter.class)
            // Faz 7: eski eklenti token dogrulamasina hic ulasmaz; RateLimit'ten sonra kaydedilmeli (ayni sira, kayit sirasi belirler).
            .addFilterBefore(new ClientVersionFilter(ClientVersion.parse(minVersion), objectMapper), ExtensionTokenAuthenticationFilter.class)
            .addFilterBefore(new RequestIdFilter(), RateLimitFilter.class)
            .addFilterAfter(new TenantRateLimitFilter(tenantRateLimiter), ExtensionTokenAuthenticationFilter.class);
```

`ClientVersion.parse` bozuk ayarda `IllegalArgumentException` fırlatır, böylece uygulama açılmaz. Spec bunu istiyor.

- [ ] **Step 10: Entegrasyon testini yaz**

```java
package com.vetos;

import com.vetos.platform.security.ClientVersionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Canli docker-compose Postgres gerekir. Faz 7 §1: min-version 0.2.0 iken eklenti zinciri
 * eski/basliksiz eklentiyi 426 ile reddeder; diger zincirler etkilenmez.
 */
@SpringBootTest(properties = "vetly.extension.min-version=0.2.0")
@AutoConfigureMockMvc
class ClientVersionEnforcementIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void should_return426WithStandardBody_when_headerMissing() throws Exception {
        mockMvc.perform(get("/api/v1/tarbil-extension/me"))
            .andExpect(status().is(426))
            .andExpect(header().string(ClientVersionFilter.MIN_HEADER, "0.2.0"))
            .andExpect(jsonPath("$.errorCode").value("CLIENT_UPGRADE_REQUIRED"))
            .andExpect(jsonPath("$.message").value(ClientVersionFilter.MESSAGE))
            .andExpect(jsonPath("$.path").value("/api/v1/tarbil-extension/me"))
            .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void should_reachTokenCheck_when_versionSupported() throws Exception {
        mockMvc.perform(get("/api/v1/tarbil-extension/me").header(ClientVersionFilter.HEADER, "0.2.0"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tarbil-extension/me").header(ClientVersionFilter.HEADER, "0.10.0"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return426_when_headerMalformed() throws Exception {
        mockMvc.perform(get("/api/v1/tarbil-extension/me").header(ClientVersionFilter.HEADER, "abc"))
            .andExpect(status().is(426));
    }

    @Test
    void should_checkPairToo() throws Exception {
        mockMvc.perform(post("/api/v1/tarbil-extension/pair")
                .header(ClientVersionFilter.HEADER, "0.1.0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"AAAA-BBBB\",\"label\":\"PC\"}"))
            .andExpect(status().is(426));
    }

    @Test
    void should_notAffectOtherChains() throws Exception {
        mockMvc.perform(get("/api/v1/patients"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 11: Entegrasyon testini ve mevcut eklenti testlerini çalıştır**

Run: `cd backend && ./mvnw test -Dtest='ClientVersion*,TarbilExtensionSecurityIntegrationTest,ExtensionTokenAuthenticationFilterTest'`
Expected: PASS. `TarbilExtensionSecurityIntegrationTest` başlık göndermez; varsayılan `0.1.0` ile geçmeye devam etmeli. Bu, "başlık yok = 0.1.0" kuralını da kanıtlar.

- [ ] **Step 12: Tüm backend testleri**

Run: `cd backend && ./mvnw test`
Expected: PASS (önceki 845 + yeni testler, hatasız).

---

### Task 2: Backend — yalnızca geliştirmede OpenAPI (springdoc)

**Files:**
- Modify: `backend/pom.xml` (`spring-boot-starter-cache` bağımlılığından sonra)
- Modify: `backend/src/main/resources/application-prod.yml` (sona)
- Modify: `backend/src/main/java/com/vetos/platform/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/vetos/OpenApiDocsTest.java`
- Test: `backend/src/test/java/com/vetos/OpenApiDocsDisabledTest.java`

**Interfaces:**
- Consumes: yok.
- Produces: `/v3/api-docs` ve `/swagger-ui.html`; bunlar `springdoc.api-docs.enabled` (varsayılan `true`) açıkken kimliksiz erişilebilir.

- [ ] **Step 1: Başarısız testleri yaz**

`OpenApiDocsTest.java`:

```java
package com.vetos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Canli docker-compose Postgres gerekir. Faz 7 §4.2: gelistirmede API dokumani kimliksiz acik. */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void should_serveApiDocs_withoutLogin() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"openapi\"")))
            .andExpect(content().string(containsString("/api/v1/tarbil-extension/pending")));
    }

    @Test
    void should_serveSwaggerUi_withoutLogin() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk());
    }
}
```

`OpenApiDocsDisabledTest.java`:

```java
package com.vetos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Canli docker-compose Postgres gerekir. application-prod.yml'deki iki bayrakla ayni ayar:
 * dokuman uretilmez VE kimliksiz istege kapali (serbest gecis eklenmez).
 */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
@AutoConfigureMockMvc
class OpenApiDocsDisabledTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void should_notServeApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Testlerin düştüğünü gör**

Run: `cd backend && ./mvnw test -Dtest='OpenApiDocs*'`
Expected: `OpenApiDocsTest` FAIL (401, çünkü springdoc yok ve izin yok). `OpenApiDocsDisabledTest` şimdiden geçebilir; sorun değil.

- [ ] **Step 3: Bağımlılığı, prod ayarını ve koşullu izni ekle**

`pom.xml`, `spring-boot-starter-cache` bağımlılığından hemen sonra:

```xml
        <!-- Faz 7: API dokumani (/v3/api-docs, /swagger-ui.html) -- yalniz gelistirmede; prod'da application-prod.yml kapatir.
             2.8.x = Boot 3.x serisi (3.x serisi Boot 4 icindir). -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.8.17</version>
        </dependency>
```

`application-prod.yml` sonuna:

```yaml

# Faz 7 (spec 2026-10-18 §4.2): API dokumani canlida kapali. SecurityConfig bu bayrak
# kapaliyken /v3/api-docs ve /swagger-ui icin serbest gecis de eklemez.
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

`SecurityConfig.java`:
- `frontendBaseUrl` alanından sonra yeni alan:

```java
    /** Faz 7: API dokumani yalniz bu bayrak acikken (gelistirme) kimliksiz; prod'da false. */
    @org.springframework.beans.factory.annotation.Value("${springdoc.api-docs.enabled:true}")
    private boolean apiDocsEnabled;
```

- `authorizeHttpRequests` bloğunu şu hale getir (mevcut yorumlar korunur):

```java
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/v1/auth/**").permitAll()
                    .requestMatchers("/api/v1/public/**").permitAll()
                    // Actuator yalnizca 127.0.0.1:8081 yonetim portunda (application.yml
                    // management.server); guvenlik ag seviyesinde. Bu zincir yonetim baglamina da
                    // uygulanabildigi icin acikca izin verilir; ana portta bu yollar zaten yok (404).
                    .requestMatchers("/actuator/**").permitAll();
                if (apiDocsEnabled) {
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                }
                auth.anyRequest().authenticated();
            })
```

- [ ] **Step 4: Testlerin geçtiğini gör**

Run: `cd backend && ./mvnw test -Dtest='OpenApiDocs*,ApplicationModulesTest,SecurityConfigCorsTest'`
Expected: PASS.

Olası durumlar:
- `/v3/api-docs` 500 dönerse springdoc bir controller'ı çözemiyor demektir. Log'daki hata satırını rapora yaz ve en dar düzeltmeyi uygula. Adrese anotasyon ekleme.
- `ApplicationModulesTest` springdoc yüzünden kırılırsa, bu `com.vetos` dışı bir paket olduğu için beklenmez; olursa raporla.

- [ ] **Step 5: Tüm backend testleri**

Run: `cd backend && ./mvnw test`
Expected: PASS.

---

### Task 3: Eklenti — 0.2.0, sürüm başlığı, 426 → UPGRADE_REQUIRED, yan panel uyarısı

**Files:**
- Create: `extension/src/shared/version.ts`
- Create: `extension/src/shared/version.test.ts`
- Create: `extension/src/sidepanel/PendingList.test.tsx`
- Modify: `extension/tsconfig.json` (`resolveJsonModule`)
- Modify: `extension/package.json` (`"version": "0.2.0"`)
- Modify: `extension/public/manifest.json` (`"version": "0.2.0"`)
- Modify: `extension/src/shared/messages.ts` (`ErrorCode`)
- Modify: `extension/src/background/vetlyApi.ts`
- Modify: `extension/src/background/vetlyApi.test.ts`
- Modify: `extension/src/background/router.ts` (sabit `VERSION` kaldırılır)
- Modify: `extension/src/background/router.test.ts:103-106`
- Modify: `extension/src/sidepanel/PendingList.tsx`

**Interfaces:**
- Produces:
  - `EXTENSION_VERSION: string` (`src/shared/version.ts`);
  - `ErrorCode` artık `'UPGRADE_REQUIRED'` içerir;
  - PING cevabı `{ ok: true, data: { version: EXTENSION_VERSION } }`. Task 5'teki web uygulaması bunu okur.
- Consumes: Task 1'deki backend sözleşmesi: 426, gövde `{ errorCode, message, ... }`, istek başlığı `X-Client-Version`.

- [ ] **Step 1: Başarısız testleri yaz**

`extension/src/shared/version.test.ts`:

```ts
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { EXTENSION_VERSION } from './version';

describe('EXTENSION_VERSION', () => {
  it('matches manifest.json and package.json', () => {
    const manifest = JSON.parse(readFileSync(new URL('../../public/manifest.json', import.meta.url), 'utf8'));
    const pkg = JSON.parse(readFileSync(new URL('../../package.json', import.meta.url), 'utf8'));
    expect(EXTENSION_VERSION).toBe(pkg.version);
    expect(manifest.version).toBe(pkg.version);
    expect(EXTENSION_VERSION).toBe('0.2.0');
  });
});
```

`vetlyApi.test.ts` içinde `describe('vetlyApi', ...)` bloğunun sonuna, importlara `import { EXTENSION_VERSION } from '../shared/version';` ekleyerek:

```ts
  it('sends X-Client-Version on every request, including pair', async () => {
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_abc');
    const { fn, calls } = fakeFetch(200, []);
    const api = createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: fn });

    await api.listPending();
    const pairFetch = fakeFetch(200, { token: 'vtx_new' });
    await createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: pairFetch.fn }).pair('K7QM-2XPA', 'PC');

    expect((calls[0].init.headers as Record<string, string>)['X-Client-Version']).toBe(EXTENSION_VERSION);
    expect((pairFetch.calls[0].init.headers as Record<string, string>)['X-Client-Version']).toBe(EXTENSION_VERSION);
  });

  it('should_mapUpgradeRequired_andKeepToken_when_426', async () => {
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_abc');
    const api = createVetlyApi({
      baseUrl: 'https://x', tokens,
      fetchFn: fakeFetch(426, { errorCode: 'CLIENT_UPGRADE_REQUIRED', message: 'Sunucudan gelen mesaj' }).fn,
    });

    await expect(api.listPending()).rejects.toMatchObject({ code: 'UPGRADE_REQUIRED', message: 'Sunucudan gelen mesaj' });
    expect(await tokens.get()).toBe('vtx_abc');
  });

  it('should_useFixedMessage_when_426BodyIsNotJson', async () => {
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_abc');
    const htmlFetch = (async () => new Response('<html>426</html>', { status: 426 })) as unknown as typeof fetch;
    const api = createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: htmlFetch });

    await expect(api.listPending()).rejects.toMatchObject({
      code: 'UPGRADE_REQUIRED',
      message: "Vetly TARBİL eklentinizin sürümü artık desteklenmiyor. Vetly'de Ayarlar > Entegrasyonlar bölümünden yeni sürümü indirin.",
    });
  });
```

`router.test.ts`'teki `answers ping` testini şöyle değiştir (importlara `import { EXTENSION_VERSION } from '../shared/version';` ekle):

```ts
  it('answers ping with the real extension version', async () => {
    const { router } = setup(async () => ({}));
    expect(await router.handleExternal({ type: 'PING' })).toEqual({ ok: true, data: { version: EXTENSION_VERSION } });
  });
```

`extension/src/sidepanel/PendingList.test.tsx`:

```tsx
// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it } from 'vitest';
import { PendingList } from './PendingList';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;

async function renderWith(reply: unknown) {
  (globalThis as unknown as { chrome: unknown }).chrome = { runtime: { sendMessage: async () => reply } };
  container = document.createElement('div');
  document.body.appendChild(container);
  let unauthorized = false;
  await act(async () => {
    createRoot(container).render(<PendingList onUnauthorized={() => { unauthorized = true; }} />);
  });
  return { container, unauthorized: () => unauthorized };
}

afterEach(() => container?.remove());

describe('PendingList', () => {
  it('shows a persistent upgrade warning without unpairing', async () => {
    const { container, unauthorized } = await renderWith({ ok: false, code: 'UPGRADE_REQUIRED', error: 'Yeni sürümü indirin.' });

    const warning = container.querySelector('.warning');
    expect(warning?.textContent).toBe('Yeni sürümü indirin.');
    expect(container.querySelector('button')).toBeNull();
    expect(unauthorized()).toBe(false);
  });

  it('keeps the offline banner with a refresh button', async () => {
    const { container } = await renderWith({ ok: false, code: 'OFFLINE', error: 'x' });

    expect(container.textContent).toContain('Çevrimdışı');
    expect(container.querySelector('button')?.textContent).toBe('Yenile');
  });
});
```

- [ ] **Step 2: Testlerin düştüğünü gör**

Run: `cd extension && npm test`
Expected: FAIL (`./version` yok; 426 → UNKNOWN; PendingList'te `.warning` yok).

- [ ] **Step 3: Uygula**

`extension/tsconfig.json`: `compilerOptions` içine `"resolveJsonModule": true,` ekle.

`extension/package.json` ve `extension/public/manifest.json`: `"version": "0.1.0"` → `"version": "0.2.0"`.

`extension/src/shared/version.ts`:

```ts
// Tek surum kaynagi: package.json. manifest.json ayni olmali (version.test.ts denetler).
// Adlandirilmis import: Vite paketin yalniz bu alanini pakete koyar.
import { version } from '../../package.json';

export const EXTENSION_VERSION: string = version;
```

`extension/src/shared/messages.ts`:

```ts
export type ErrorCode = 'UNAUTHORIZED' | 'OFFLINE' | 'NOT_FOUND' | 'CONFLICT' | 'UPGRADE_REQUIRED' | 'UNKNOWN';
```

`extension/src/background/vetlyApi.ts`:
- importlara `import { EXTENSION_VERSION } from '../shared/version';` ekle.
- `ApiError` sınıfından sonra şu iki tanımı ekle:

```ts
/** Backend 426 (Faz 7) mesaji okunamazsa gosterilir; backend'deki ClientVersionFilter.MESSAGE ile ayni. */
export const UPGRADE_MESSAGE =
  "Vetly TARBİL eklentinizin sürümü artık desteklenmiyor. Vetly'de Ayarlar > Entegrasyonlar bölümünden yeni sürümü indirin.";

async function upgradeMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { message?: unknown };
    return typeof body.message === 'string' && body.message ? body.message : UPGRADE_MESSAGE;
  } catch {
    return UPGRADE_MESSAGE;
  }
}
```

- `request()` içinde başlık satırını ve 426 kontrolünü şöyle yaz (401 kontrolünden önce):

```ts
    const headers: Record<string, string> = { 'Content-Type': 'application/json', 'X-Client-Version': EXTENSION_VERSION };
```

```ts
    // 426: eklenti cok eski (Faz 7). Token SILINMEZ -- guncelleme sonrasi yeniden eslestirme gerekmesin.
    if (response.status === 426) throw new ApiError('UPGRADE_REQUIRED', await upgradeMessage(response));
```

`extension/src/background/router.ts`:
- `const VERSION = '0.1.0';` satırını sil;
- `import { EXTENSION_VERSION } from '../shared/version';` ekle;
- PING dalında `{ version: VERSION }` → `{ version: EXTENSION_VERSION }`.

`extension/src/sidepanel/PendingList.tsx`, hata durumunu tutan kısımlar:

```tsx
  const [error, setError] = useState<{ message: string; upgrade: boolean } | null>(null);

  async function load() {
    const res = await sendToBackground<Submission[]>({ type: 'LIST_PENDING' });
    if (res.ok) {
      setItems(res.data);
      setError(null);
    } else if (res.code === 'UNAUTHORIZED') {
      onUnauthorized();
    } else {
      setError({
        message: res.code === 'OFFLINE' ? 'Çevrimdışı — Vetly\'ye ulaşılamıyor.' : res.error,
        upgrade: res.code === 'UPGRADE_REQUIRED',
      });
    }
  }
```

```tsx
  // Guncelleme gerekiyorsa kalici uyari: yenilemek ise yaramaz, yeni surum kurulmali.
  if (error?.upgrade) return <div className="warning">{error.message}</div>;
  if (error) return <div className="banner">{error.message} <button onClick={load}>Yenile</button></div>;
```

Eşleştirme ekranı (`PairingView.tsx`) değişmez. `UNAUTHORIZED` dışındaki hatalarda `res.error` metnini zaten `.warning` kutusunda gösteriyor, 426 mesajı da orada çıkar.

- [ ] **Step 4: Testler ve tip denetimi**

Run: `cd extension && npm test && npx tsc --noEmit`
Expected: Tüm vitest testleri PASS, tsc hatasız.

- [ ] **Step 5: Derleme**

Run: `cd extension && npm run build`
Expected:
- Hatasız.
- `dist/manifest.json` içinde `"version": "0.2.0"`.
- `grep -c '"0.2.0"' dist/background.js` en az 1 döner.
- `grep -c 'devDependencies' dist/background.js` 0 döner; yani package.json'ın tamamı pakete girmemiş.

---

### Task 4: Eklenti — `npm run package` (zip + sürüm json'u)

**Files:**
- Create: `extension/scripts/package.mjs`
- Modify: `extension/package.json` (script + `fflate` devDependency)
- Modify: `.gitignore` (kök)
- Modify: `extension/README.md` ("Kurulum (paketlenmemiş)" bölümü)

**Interfaces:**
- Consumes: Task 3'teki `dist/` çıktısı (`manifest.json` sürümü 0.2.0).
- Produces: `frontend/public/downloads/vetly-tarbil-eklenti.zip` (zip kökünde doğrudan `manifest.json`) ve `frontend/public/downloads/vetly-tarbil-eklenti.json` (`{"version":"0.2.0"}` ve satır sonu). Task 5 bu iki adresi kullanır.

- [ ] **Step 1: Bağımlılığı ekle**

Run: `cd extension && npm install --save-dev --save-exact fflate@0.8.2`
Expected: `package.json` `devDependencies` içinde `"fflate": "0.8.2"`, `package-lock.json` güncellenir.

- [ ] **Step 2: Betiği yaz**

`extension/scripts/package.mjs`:

```js
// Faz 7: derlenmis eklentiyi (dist/) web uygulamasinin indirme klasorune zip'ler ve
// yanina en yeni surumu yazar. Kabuk komutu kullanmaz (Windows + Linux).
// Kullanim: npm run package  (once build calisir)
import { mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { zipSync } from 'fflate';

const root = fileURLToPath(new URL('..', import.meta.url));
const dist = join(root, 'dist');
const outDir = join(root, '..', 'frontend', 'public', 'downloads');

const { version } = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));
const manifest = JSON.parse(readFileSync(join(dist, 'manifest.json'), 'utf8'));
if (manifest.version !== version) {
  console.error(`dist/manifest.json surumu (${manifest.version}) package.json (${version}) ile ayni degil.`);
  process.exit(1);
}

function collect(dir, files = {}) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) collect(full, files);
    else files[relative(dist, full).split(sep).join('/')] = readFileSync(full);
  }
  return files;
}

mkdirSync(outDir, { recursive: true });
writeFileSync(join(outDir, 'vetly-tarbil-eklenti.zip'), zipSync(collect(dist), { level: 9 }));
writeFileSync(join(outDir, 'vetly-tarbil-eklenti.json'), JSON.stringify({ version }) + '\n');
console.log(`vetly-tarbil-eklenti ${version} -> ${outDir}`);
```

`extension/package.json` `scripts` içine (`extension-id` satırından önce):

```json
    "package": "npm run build && node scripts/package.mjs",
```

Kök `.gitignore` dosyasında `# TARBIL eklentisi` bölümünün sonuna:

```
# Faz 7: npm run package ciktisi (derleme urunu, repoda tutulmaz)
frontend/public/downloads/
```

- [ ] **Step 3: Çalıştır ve doğrula**

Run: `cd extension && npm run package`
Expected: Son satır `vetly-tarbil-eklenti 0.2.0 -> ...frontend/public/downloads`.

Run (zip içeriği ve json):

```bash
cd extension && node -e "
const { unzipSync } = require('fflate');
const fs = require('fs');
const files = Object.keys(unzipSync(fs.readFileSync('../frontend/public/downloads/vetly-tarbil-eklenti.zip')));
for (const f of ['manifest.json','background.js','content.js','page.js','sidepanel.html']) if (!files.includes(f)) { console.error('EKSIK', f); process.exit(1); }
if (files.some(f => f.includes('key.pem'))) { console.error('key.pem zipte'); process.exit(1); }
console.log('OK', files.length, 'dosya');
console.log(fs.readFileSync('../frontend/public/downloads/vetly-tarbil-eklenti.json','utf8'));
"
```

Expected:
- `OK <n> dosya` ve `{"version":"0.2.0"}`.
- `git status --short frontend/public/downloads` boş döner (gitignore çalışıyor).

Not: `sidepanel.html` dist'te farklı bir yoldaysa (ör. alt klasörde), kontrol listesini `dist/` içindeki gerçek yola göre düzelt ve bunu raporda belirt.

- [ ] **Step 4: README'yi güncelle**

`extension/README.md` içindeki `## Kurulum (paketlenmemiş)` bölümünün yerine:

```markdown
## Yayınlama ve kurulum (Faz 7)

Yeni sürüm: `package.json` ve `public/manifest.json`'daki `version`'ı birlikte artırın (test ikisinin eşitliğini denetler), sonra:

    npm run package        # build + ../frontend/public/downloads/vetly-tarbil-eklenti.zip ve .json

Ardından web uygulamasını yayınlayın (`docs/deployment.md`). Klinikler eklentiyi **Vetly > Ayarlar > Entegrasyonlar > TARBİL Chrome Eklentisi** kartından indirir:
zip'i bir klasöre açın, Chrome > `chrome://extensions` > Geliştirici modu > "Paketlenmemiş öğe yükle" > o klasör.
Güncellemede klasörün içeriği yenisiyle değiştirilir ve eklenti kartında "Yenile"ye basılır; kimlik ve eşleştirme korunur.

Eklenti her isteğe `X-Client-Version` ekler; sunucu en düşük sürümün (`VETLY_EXTENSION_MIN_VERSION`) altındaki eklentiye 426 döner,
yan panel "yeni sürümü indirin" uyarısı gösterir. Kurallar: `docs/api-conventions.md` > Versiyonlama.

Geliştirmede doğrudan `extension/dist` klasörü de yüklenebilir.
```

---

### Task 5: Web uygulaması — sürüm kartı ve indirme (Ayarlar > Entegrasyonlar)

**Files:**
- Modify: `frontend/package.json` (`test` betiği + `vitest`, `jsdom` devDependencies)
- Create: `frontend/vitest.config.ts`
- Create: `frontend/src/lib/version.ts`
- Create: `frontend/src/lib/version.test.ts`
- Create: `frontend/src/lib/extensionRelease.ts`
- Create: `frontend/src/lib/extensionRelease.test.ts`
- Modify: `frontend/src/lib/tarbilExtension.ts` (`getInstalledExtensionVersion`)
- Modify: `frontend/src/pages/settings/TarbilExtensionCard.tsx`
- Modify: `frontend/src/pages/settings/SettingsPage.module.css` (sona yeni sınıflar)
- Create: `frontend/src/pages/settings/TarbilExtensionCard.test.tsx`

**Interfaces:**
- Consumes:
  - Task 3: eklentinin PING cevabı `{ ok: true, data: { version: string } }`. Eski 0.1.0 eklenti de `{ version: '0.1.0' }` döner.
  - Task 4: `/downloads/vetly-tarbil-eklenti.zip` ve `/downloads/vetly-tarbil-eklenti.json`.
- Produces:
  - `compareVersions(a: string, b: string): number`;
  - `fetchLatestExtensionVersion(fetchFn?: typeof fetch): Promise<string | null>`;
  - `extensionVersionStatus(installed: string | null, latest: string | null): ExtensionVersionStatus`;
  - `getInstalledExtensionVersion(): Promise<string | null>`;
  - `EXTENSION_DOWNLOAD_URL`, `EXTENSION_RELEASE_URL`.

- [ ] **Step 1: Test altyapısını kur**

Run: `cd frontend && npm install --save-dev vitest@^2.1.0 jsdom@^25.0.1`

`frontend/package.json` `scripts` içine `"preview"` satırından sonra:

```json
    "test": "vitest run",
```

`frontend/vitest.config.ts`:

```ts
import { defineConfig } from 'vitest/config';

// Birim/bilesen testleri (Faz 7'de eklendi). Playwright e2e ayri: npm run test:e2e.
export default defineConfig({
  test: { environment: 'node', include: ['src/**/*.test.ts', 'src/**/*.test.tsx'] },
});
```

- [ ] **Step 2: Başarısız testleri yaz**

`frontend/src/lib/version.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { compareVersions } from './version';

describe('compareVersions', () => {
  it('compares numerically, not as text', () => {
    expect(compareVersions('0.10.0', '0.9.0')).toBeGreaterThan(0);
    expect(compareVersions('0.1.0', '0.2.0')).toBeLessThan(0);
    expect(compareVersions('0.2.0', '0.2.0')).toBe(0);
    expect(compareVersions(' 1.0.0 ', '0.99.99')).toBeGreaterThan(0);
  });

  it('treats malformed versions as 0.0.0', () => {
    expect(compareVersions('abc', '0.0.0')).toBe(0);
    expect(compareVersions('abc', '0.1.0')).toBeLessThan(0);
    expect(compareVersions('1.2', '0.0.1')).toBeLessThan(0);
  });
});
```

`frontend/src/lib/extensionRelease.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { extensionVersionStatus, fetchLatestExtensionVersion } from './extensionRelease';

const respond = (body: string, status = 200) => (async () => new Response(body, { status })) as unknown as typeof fetch;

describe('fetchLatestExtensionVersion', () => {
  it('reads the version from the release json', async () => {
    expect(await fetchLatestExtensionVersion(respond('{"version":"0.2.0"}'))).toBe('0.2.0');
  });

  it('returns null when the file is missing, is HTML (SPA fallback) or has no version', async () => {
    expect(await fetchLatestExtensionVersion(respond('not found', 404))).toBeNull();
    expect(await fetchLatestExtensionVersion(respond('<!doctype html><html></html>'))).toBeNull();
    expect(await fetchLatestExtensionVersion(respond('{"foo":1}'))).toBeNull();
    const failing = (async () => { throw new TypeError('offline'); }) as unknown as typeof fetch;
    expect(await fetchLatestExtensionVersion(failing)).toBeNull();
  });
});

describe('extensionVersionStatus', () => {
  it('covers every state', () => {
    expect(extensionVersionStatus(null, '0.2.0')).toEqual({ kind: 'not-installed' });
    expect(extensionVersionStatus('0.2.0', '0.2.0')).toEqual({ kind: 'up-to-date', installed: '0.2.0' });
    expect(extensionVersionStatus('0.3.0', '0.2.0')).toEqual({ kind: 'up-to-date', installed: '0.3.0' });
    expect(extensionVersionStatus('0.1.0', '0.2.0')).toEqual({ kind: 'outdated', installed: '0.1.0', latest: '0.2.0' });
    expect(extensionVersionStatus('0.9.0', '0.10.0')).toEqual({ kind: 'outdated', installed: '0.9.0', latest: '0.10.0' });
    expect(extensionVersionStatus('0.2.0', null)).toEqual({ kind: 'installed', installed: '0.2.0' });
  });
});
```

`frontend/src/pages/settings/TarbilExtensionCard.test.tsx`:

```tsx
// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/tarbilApi', () => ({
  tarbilApi: {
    extensionTokens: vi.fn().mockResolvedValue([]),
    createPairingCode: vi.fn(),
    revokeExtensionToken: vi.fn(),
  },
}));
vi.mock('../../lib/tarbilExtension', () => ({ getInstalledExtensionVersion: vi.fn() }));

import { getInstalledExtensionVersion } from '../../lib/tarbilExtension';
import { TarbilExtensionCard } from './TarbilExtensionCard';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;

async function render(installed: string | null, releaseJson: string | null) {
  vi.mocked(getInstalledExtensionVersion).mockResolvedValue(installed);
  vi.stubGlobal('fetch', async () =>
    releaseJson === null ? new Response('not found', { status: 404 }) : new Response(releaseJson, { status: 200 }));
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(<TarbilExtensionCard />);
  });
  await act(async () => {});
  const link = container.querySelector<HTMLAnchorElement>('a[download]');
  return { text: container.textContent ?? '', link };
}

beforeEach(() => vi.clearAllMocks());
afterEach(() => {
  container?.remove();
  vi.unstubAllGlobals();
});

describe('TarbilExtensionCard version row', () => {
  it('not installed', async () => {
    const { text, link } = await render(null, '{"version":"0.2.0"}');
    expect(text).toContain('Eklenti yüklü değil. İndirip kurun.');
    expect(link?.getAttribute('href')).toBe('/downloads/vetly-tarbil-eklenti.zip');
  });

  it('up to date', async () => {
    const { text } = await render('0.2.0', '{"version":"0.2.0"}');
    expect(text).toContain('Yüklü sürüm: 0.2.0 (güncel)');
  });

  it('outdated', async () => {
    const { text, link } = await render('0.1.0', '{"version":"0.2.0"}');
    expect(text).toContain('Yüklü sürüm: 0.1.0. Yeni sürüm var (0.2.0), indirip güncelleyin.');
    expect(link).not.toBeNull();
  });

  it('release file missing', async () => {
    const { text, link } = await render('0.1.0', null);
    expect(text).toContain('Eklenti dosyası şu an hazır değil');
    expect(text).toContain('Yüklü sürüm: 0.1.0');
    expect(text).not.toContain('Yeni sürüm var');
    expect(link).toBeNull();
  });

  it('shows install and update steps', async () => {
    const { text } = await render(null, '{"version":"0.2.0"}');
    expect(text).toContain('chrome://extensions');
    expect(text).toContain('Paketlenmemiş öğe yükle');
  });
});
```

- [ ] **Step 3: Testlerin düştüğünü gör**

Run: `cd frontend && npm test`
Expected: FAIL (modüller yok, kartta sürüm satırı yok).

- [ ] **Step 4: Yardımcıları yaz**

`frontend/src/lib/version.ts`:

```ts
// Eklenti surumu karsilastirmasi (x.y.z, sayisal) -- backend ClientVersion ile ayni kural:
// bozuk surum 0.0.0 sayilir; "0.10.0" > "0.9.0".
function parts(v: string): [number, number, number] {
  const m = /^(\d{1,9})\.(\d{1,9})\.(\d{1,9})$/.exec(v.trim());
  return m ? [Number(m[1]), Number(m[2]), Number(m[3])] : [0, 0, 0];
}

export function compareVersions(a: string, b: string): number {
  const pa = parts(a);
  const pb = parts(b);
  for (let i = 0; i < 3; i++) {
    if (pa[i] !== pb[i]) return pa[i] - pb[i];
  }
  return 0;
}
```

`frontend/src/lib/extensionRelease.ts`:

```ts
import { compareVersions } from './version';

// extension/ icinde `npm run package` uretir (Faz 7); dosyalar repoda tutulmaz.
export const EXTENSION_DOWNLOAD_URL = '/downloads/vetly-tarbil-eklenti.zip';
export const EXTENSION_RELEASE_URL = '/downloads/vetly-tarbil-eklenti.json';

/** Sunucudaki en yeni eklenti surumu; dosya yoksa/HTML donerse (SPA yonlendirmesi) null. */
export async function fetchLatestExtensionVersion(fetchFn: typeof fetch = fetch): Promise<string | null> {
  try {
    const res = await fetchFn(EXTENSION_RELEASE_URL, { cache: 'no-store' });
    if (!res.ok) return null;
    const body = (await res.json()) as { version?: unknown };
    return typeof body.version === 'string' ? body.version : null;
  } catch {
    return null;
  }
}

export type ExtensionVersionStatus =
  | { kind: 'not-installed' }
  | { kind: 'installed'; installed: string }
  | { kind: 'up-to-date'; installed: string }
  | { kind: 'outdated'; installed: string; latest: string };

export function extensionVersionStatus(installed: string | null, latest: string | null): ExtensionVersionStatus {
  if (!installed) return { kind: 'not-installed' };
  if (!latest) return { kind: 'installed', installed };
  return compareVersions(installed, latest) < 0
    ? { kind: 'outdated', installed, latest }
    : { kind: 'up-to-date', installed };
}
```

`frontend/src/lib/tarbilExtension.ts`, `isExtensionAvailable` fonksiyonundan sonra:

```ts
/** Yuklu eklentinin surumu (PING); eklenti yoksa ya da yanit vermezse null. */
export async function getInstalledExtensionVersion(): Promise<string | null> {
  const reply = await send({ type: 'PING' });
  if (!reply?.ok) return null;
  const version = (reply.data as { version?: unknown } | null)?.version;
  return typeof version === 'string' ? version : null;
}
```

- [ ] **Step 5: Kartı güncelle**

`TarbilExtensionCard.tsx`:
- importlara ekle:

```tsx
import {
  EXTENSION_DOWNLOAD_URL,
  ExtensionVersionStatus,
  extensionVersionStatus,
  fetchLatestExtensionVersion,
} from '../../lib/extensionRelease';
import { getInstalledExtensionVersion } from '../../lib/tarbilExtension';
```

- bileşenin dışına (dosya sonuna) yardımcıyı ekle:

```tsx
function versionText(status: ExtensionVersionStatus): string {
  switch (status.kind) {
    case 'not-installed':
      return 'Eklenti yüklü değil. İndirip kurun.';
    case 'installed':
      return `Yüklü sürüm: ${status.installed}`;
    case 'up-to-date':
      return `Yüklü sürüm: ${status.installed} (güncel)`;
    case 'outdated':
      return `Yüklü sürüm: ${status.installed}. Yeni sürüm var (${status.latest}), indirip güncelleyin.`;
  }
}
```

- bileşen içinde, mevcut state'lerden sonra:

```tsx
  const [release, setRelease] = useState<{ installed: string | null; latest: string | null } | null>(null);

  useEffect(() => {
    Promise.all([getInstalledExtensionVersion(), fetchLatestExtensionVersion()]).then(([installed, latest]) =>
      setRelease({ installed, latest }),
    );
  }, []);
```

- JSX'te `integrationHeader` div'inin kapanışından hemen sonra (eşleştirme kodu bloğundan önce):

```tsx
      {release && (
        <div className={styles.extensionRelease}>
          {(() => {
            const status = extensionVersionStatus(release.installed, release.latest);
            return (
              <div className={status.kind === 'outdated' ? styles.versionWarning : styles.muted}>{versionText(status)}</div>
            );
          })()}
          {release.latest ? (
            <a className={styles.downloadLink} href={EXTENSION_DOWNLOAD_URL} download>
              Eklentiyi indir ({release.latest})
            </a>
          ) : (
            <div className={styles.muted}>Eklenti dosyası şu an hazır değil</div>
          )}
          <details className={styles.installSteps}>
            <summary>Kurulum ve güncelleme</summary>
            <ol>
              <li>İndirdiğiniz zip dosyasını bir klasöre açın (ör. Belgeler\Vetly TARBİL).</li>
              <li>
                Chrome adres çubuğuna <code>chrome://extensions</code> yazın ve sağ üstten "Geliştirici modu"nu açın.
              </li>
              <li>"Paketlenmemiş öğe yükle"ye basıp açtığınız klasörü seçin.</li>
              <li>
                Güncellerken: aynı klasörün içeriğini yeni zip'teki dosyalarla değiştirin, eklentiler sayfasında eklentinin
                "Yenile" düğmesine basın. Eşleştirme korunur, yeniden kod girmeniz gerekmez.
              </li>
            </ol>
          </details>
        </div>
      )}
```

`SettingsPage.module.css` sonuna:

```css
/* Faz 7: TARBIL eklentisi surum satiri + indirme */
.extensionRelease {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
  font-size: 13px;
}
.versionWarning {
  color: var(--color-warning-700);
  font-weight: 600;
}
.downloadLink {
  align-self: flex-start;
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  color: inherit;
  text-decoration: none;
  font-weight: 600;
}
.installSteps {
  color: var(--color-text-muted);
}
.installSteps ol {
  margin: 6px 0 0;
  padding-left: 18px;
}
```

- [ ] **Step 6: Testler, tip denetimi, derleme**

Run: `cd frontend && npm test && npm run build`
Expected: vitest PASS (version 2, extensionRelease 3, kart 5). `tsc -b && vite build` hatasız.

Not: `tsc -b` test dosyalarını da denetler. `vi.mocked` ve `vi.stubGlobal` tipleri vitest'ten gelir. Hata çıkarsa ilgili satırda en dar tip düzeltmesini yap ve raporla.

---

### Task 6: Dokümantasyon ve yol haritası

**Files:**
- Modify: `docs/api-conventions.md` (yetki matrisi `/tarbil-extension/**` satırı; `## Versiyonlama` bölümü)
- Modify: `docs/deployment.md` (`## TARBİL eklentisi` bölümü; `## Ortam değişkenleri` listesi; yeni `## Eklenti dağıtımı ve API dokümanı (Faz 7)` bölümü `## Önbellek (Faz 6)` bölümünden sonra)
- Modify: `docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md` (Faz 7/Faz 8 başlıkları)

**Interfaces:** Önceki görevlerdeki adlar birebir kullanılır: `X-Client-Version`, `X-Min-Client-Version`, `CLIENT_UPGRADE_REQUIRED`, `VETLY_EXTENSION_MIN_VERSION`, `npm run package`, `/downloads/vetly-tarbil-eklenti.zip`.

- [ ] **Step 1: `docs/api-conventions.md`**

Yetki matrisindeki satır:

```markdown
| `/tarbil-extension/**` (yalnız eklenti anahtarı; `/pair` herkese açık + hız sınırlı; sürüm kontrolü: bkz. Versiyonlama) | eklenti | eklenti | eklenti | eklenti |
```

`## Versiyonlama` bölümünün tamamını şununla değiştir:

```markdown
## Versiyonlama

`v1` şu an tek versiyon (spec: `docs/superpowers/specs/2026-10-18-api-sozlesmesi-faz7-design.md`).

**Kırıcı değişiklik** (mevcut bir istemciyi bozar, sürüm gerektirir):
- bir alanı silmek ya da adını değiştirmek,
- bir alanın tipini değiştirmek,
- zorunlu yeni bir girdi (alan, parametre, başlık) eklemek,
- bir adresi kaldırmak,
- durum kodlarının ya da `errorCode` değerlerinin anlamını değiştirmek.

**Kırıcı olmayan değişiklik** (sürüm gerekmez): yeni alan, isteğe bağlı yeni girdi, yeni adres. İstemciler (web uygulaması, eklenti) tanımadıkları alanları görmezden gelmek zorundadır.

**v2 kuralı:** Kırıcı değişiklik gerekirse yalnız etkilenen adresler için `/api/v2/...` açılır; `v1` karşılığı en az 6 ay daha çalışır.

**Kullanımdan kaldırma:** Kalkacak bir adres cevaplarında `Deprecation: true` ve `Sunset: <HTTP tarihi>` (kapanış tarihi) başlıklarını taşır. Bugün kalkacak adres yok; kural olarak duruyor, kodda karşılığı yok.

**Web uygulaması ↔ backend:** Her zaman birlikte yayınlanır; aralarında sürüm kontrolü yoktur.

### TARBİL eklentisi: zorunlu güncelleme

Eklenti sunucuyla birlikte yayınlanmayan tek istemcidir. Her isteğe `X-Client-Version: x.y.z` ekler (kaynak: `extension/package.json`).
`/api/v1/tarbil-extension/**` (`/pair` dahil) altında backend bunu `VETLY_EXTENSION_MIN_VERSION`'la (`vetly.extension.min-version`, varsayılan `0.1.0`) sayısal karşılaştırır:
- Başlık yoksa `0.1.0`, bozuksa `0.0.0` sayılır.
- Eskiyse **426** döner: `errorCode: CLIENT_UPGRADE_REQUIRED`, Türkçe güncelleme mesajı, `X-Min-Client-Version` başlığı. Eklenti yan panelde kalıcı uyarı gösterir, eşleştirmeyi silmez.

En düşük sürümü yükseltme sırası:
1. Yeni eklenti sürümünü `extension/` içinde `npm run package` ile paketleyip web uygulamasını yayınlayın.
2. Klinikler güncellesin diye bekleyin (Ayarlar > Entegrasyonlar'daki kart eski sürümde "Yeni sürüm var" der).
3. Ancak sonra sunucuda `VETLY_EXTENSION_MIN_VERSION`'ı yükseltip backend'i yeniden başlatın.

### API dokümanı (OpenAPI)

Geliştirmede `/swagger-ui.html` (JSON: `/v3/api-docs`) kimliksiz açıktır; içerik koddan otomatik çıkar, adreslere anotasyon eklenmez. `prod` profilinde kapalıdır.
```

- [ ] **Step 2: `docs/deployment.md`**

`## TARBİL eklentisi` bölümündeki kod bloğunu ve "Kurulum (her klinik bilgisayarında...)" paragrafını şununla değiştir. Sonraki iki madde işaretli satır (eklenti kimliği, "Eklentiyi bağla") olduğu gibi kalır.

````markdown
```powershell
cd <repo>\extension
npm ci
npm run package        # build + ..\frontend\public\downloads\vetly-tarbil-eklenti.zip ve .json
```
Ardından **Frontend güncellemesi** adımlarıyla web uygulamasını yayınlayın; zip `dist/downloads/` içinde sunucuya gider. Klinikler eklentiyi Vetly > Ayarlar > Entegrasyonlar > TARBİL kartından indirip "Paketlenmemiş öğe yükle" ile kurar; kart yüklü sürümü ve yeni sürüm olup olmadığını gösterir.
````

`## Ortam değişkenleri` listesine, "İsteğe bağlı entegrasyonlar" maddesinden sonra:

```markdown
- `VETLY_EXTENSION_MIN_VERSION` (varsayılan `0.1.0`): bundan eski TARBİL eklentisi 426 alır. Yükseltmeden önce yeni sürüm Vetly'den indirilebilir olmalı (sıra: `docs/api-conventions.md` > Versiyonlama).
```

`## Önbellek (Faz 6)` bölümünden sonra, `## Bilinen açık işler` bölümünden önce:

```markdown
## Eklenti dağıtımı ve API dokümanı (Faz 7)

- **Sıra:** frontend derlenmeden önce `extension/` içinde `npm ci && npm run package`. Unutulursa Ayarlar'daki kart "Eklenti dosyası şu an hazır değil" der (indirme 404 vermez, kart bozulmaz).
- `frontend/public/downloads/` repoda değildir (derleme çıktısı). `key.pem` gerekmez: kimliği `manifest.json`'daki `key` alanı sabit tutar.
- **Zorunlu güncelleme:** `VETLY_EXTENSION_MIN_VERSION` yükseltilince eski eklenti 426 alır. 0.1.0 eklentiler 426'yı tanımadığı için bir kez "Beklenmeyen yanıt: 426" gösterir. Bu yüzden önce kart üzerinden güncellemeyi duyurun.
- **API dokümanı:** `/swagger-ui.html` yalnız geliştirmede; `application-prod.yml` (`springdoc.*.enabled: false`) canlıda hem üretimi hem kimliksiz erişimi kapatır. Kontrol: `curl -s -o /dev/null -w '%{http_code}' https://uygulama.vetly.com.tr/v3/api-docs` → `401`.
```

- [ ] **Step 3: Yol haritası**

`docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md` dosyasında:
- `## Faz 7 — API sözleşmesi ve versiyonlama ← **SIRADAKİ**` → `## Faz 7 — API sözleşmesi ve versiyonlama ✅`.
- Faz 7 maddelerinin sonuna:

```markdown
- ✅ 2026-10-18: eklenti `X-Client-Version` + 426 `CLIENT_UPGRADE_REQUIRED` (`VETLY_EXTENSION_MIN_VERSION`), eklenti 0.2.0 Vetly'den indiriliyor (Ayarlar > Entegrasyonlar, sürüm kartı), versiyonlama kuralları `docs/api-conventions.md`'de, springdoc yalnız geliştirmede (spec docs/superpowers/specs/2026-10-18-api-sozlesmesi-faz7-design.md). `Deprecation`/`Sunset` yalnız yazılı kural.
```

- `## Faz 8 — Server-Sent Events` → `## Faz 8 — Server-Sent Events ← **SIRADAKİ**`.

- [ ] **Step 4: Son doğrulama**

Run:

```bash
cd backend && ./mvnw test && cd ../extension && npm test && npx tsc --noEmit && cd ../frontend && npm test && npm run build
```

Expected: Hepsi PASS. `git status --short` yalnız bu plandaki dosyaları (ve `package-lock.json` değişikliklerini) gösterir; `frontend/public/downloads/` listede yoktur.
