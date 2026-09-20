# Güvenlik Sertleştirme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/api/v1/auth/**` ve `/api/v1/public/**` uç noktalarına IP bazlı rate limiting eklemek (kaba kuvvet/spam koruması) ve prod ortamında `http://localhost:*` CORS izninin devre dışı kalmasını sağlamak.

**Architecture:** `platform/web` paketine, sadece kimlik doğrulaması gerektirmeyen iki uç nokta grubu için bellek-içi (bucket4j) rate limit uygulayan bir `OncePerRequestFilter` eklenir; `SecurityConfig`'e `JwtAuthenticationFilter`'dan önce eklenir. `SecurityConfig.corsConfigurationSource`, aktif Spring profiline (`Environment`) bakarak `prod` dışında `http://localhost:*` desenini ekler, `prod`'da hiç eklemez.

**Tech Stack:** `com.bucket4j:bucket4j-core` (bellek-içi, Redis gerekmiyor), Spring Security filter chain, JUnit 5 + Mockito.

**Spec:** `docs/superpowers/specs/2026-09-19-guvenlik-sertlestirme-design.md`

## Global Constraints

- Rate limit sayıları: `/api/v1/auth/**` → 5 dakikada 10 istek; `/api/v1/public/**` → 1 dakikada 60 istek. Aşılırsa HTTP 429, gövde: `{"errorCode":"RATE_LIMITED","message":"Cok fazla istek, lutfen biraz sonra tekrar deneyin"}`.
- Rate limiting sadece bu iki path prefix'i için çalışır — kimlik doğrulanmış trafik bu filtreden etkilenmeden geçer.
- İstemci IP'si `X-Forwarded-For` header'ı varsa ondan (ilk değer), yoksa `request.getRemoteAddr()`'dan alınır (Render gibi ters proxy arkasında doğru IP için).
- CORS'ta `http://localhost:*` deseni sadece **prod dışı** profillerde (`env.getActiveProfiles()` içinde `"prod"` yoksa) eklenir; `vetlySiteOrigin`/`frontendBaseUrl` her profilde eklenmeye devam eder.
- Bu kod tabanında pure Spring config/wiring sınıfları için ayrı birim test yazılmaz — ama `corsConfigurationSource` burada gerçek koşullu mantık içerdiği için (profile kontrolü), spec açıkça bir birim test istiyor; bu istisna korunuyor.
- `ApplicationModulesTest` her görev sonunda yeşil kalmalı.
- Her görev sonunda `cd backend && ./mvnw test` çalıştırılır.

---

## Task 1: IP Bazlı Rate Limiting

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/vetos/platform/web/RateLimitFilter.java`
- Modify: `backend/src/main/java/com/vetos/platform/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/vetos/platform/web/RateLimitFilterTest.java`

**Interfaces:**
- Produces: `RateLimitFilter` (public, `OncePerRequestFilter`) — `SecurityConfig` `new`'ler, Spring bean değil.

- [ ] **Step 1: `pom.xml`'e bucket4j bağımlılığını ekle**

`backend/pom.xml` — `spring-boot-starter-actuator` bağımlılığından hemen sonra (satır ~57), diğer bağımlılık bloklarından önce ekle:
```xml
        <dependency>
            <groupId>com.bucket4j</groupId>
            <artifactId>bucket4j-core</artifactId>
            <version>8.10.1</version>
        </dependency>
```

- [ ] **Step 2: Derlemenin bozulmadığını doğrula**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: hatasız derleme (bağımlılık Maven Central'dan çekilir).

- [ ] **Step 3: `RateLimitFilterTest`'i yaz (önce başarısız olacak şekilde)**

`backend/src/test/java/com/vetos/platform/web/RateLimitFilterTest.java`:
```java
package com.vetos.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    @Test
    void should_passThrough_when_pathNotAuthOrPublic() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/notifications/status");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void should_allowRequests_withinAuthLimit() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, response, chain);
        }

        verify(chain, times(10)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void should_reject_when_authLimitExceeded() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, response, chain);
        }
        filter.doFilter(request, response, chain);

        verify(chain, times(10)).doFilter(request, response);
        verify(response).setStatus(429);
        verify(response).setContentType("application/json");
        assertThat(body.toString()).contains("RATE_LIMITED");
    }

    @Test
    void should_useXForwardedFor_when_present() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");
        StringWriter body = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(body));

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, response, chain);
        }
        filter.doFilter(request, response, chain);

        verify(response).setStatus(429);
        verify(request, never()).getRemoteAddr();
    }

    @Test
    void should_trackDifferentIps_independently() throws ServletException, IOException {
        HttpServletRequest requestA = mock(HttpServletRequest.class);
        HttpServletRequest requestB = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(requestA.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(requestA.getRemoteAddr()).thenReturn("10.0.0.10");
        when(requestB.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(requestB.getRemoteAddr()).thenReturn("10.0.0.20");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(requestA, response, chain);
        }
        filter.doFilter(requestB, response, chain);

        verify(response, never()).setStatus(429);
    }

    @Test
    void should_allowMoreRequests_forPublicPath_thanAuthPath() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/public/appointments");
        when(request.getRemoteAddr()).thenReturn("10.0.0.30");

        for (int i = 0; i < 60; i++) {
            filter.doFilter(request, response, chain);
        }

        verify(chain, times(60)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }
}
```

- [ ] **Step 4: Testi çalıştır, derleme hatasıyla başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q test -Dtest=RateLimitFilterTest`
Expected: derleme hatası — `RateLimitFilter` sınıfı henüz yok.

- [ ] **Step 5: `RateLimitFilter`'ı oluştur**

`backend/src/main/java/com/vetos/platform/web/RateLimitFilter.java`:
```java
package com.vetos.platform.web;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sadece kimlik dogrulama gerektirmeyen uc noktalar icin (auth + public) --
 * kimlik dogrulanmis trafik bu filtreden gecmez, kiraci basina adil
 * paylasim ayri/daha karmasik bir konu, bu turun kapsaminda degil.
 * Bellek-ici (per-instance) -- coklu instance'ta limit instance sayisiyla
 * orantili gevser, kabul edilebilir (bkz. tasarimin bagimlilik notu).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> publicBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        Map<String, Bucket> buckets;
        Bandwidth limit;
        if (path.startsWith("/api/v1/auth/")) {
            buckets = authBuckets;
            limit = Bandwidth.builder().capacity(10).refillIntervally(10, Duration.ofMinutes(5)).build();
        } else if (path.startsWith("/api/v1/public/")) {
            buckets = publicBuckets;
            limit = Bandwidth.builder().capacity(60).refillIntervally(60, Duration.ofMinutes(1)).build();
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = clientIp(request);
        Bucket bucket = buckets.computeIfAbsent(clientIp, ip -> Bucket.builder().addLimit(limit).build());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"errorCode\":\"RATE_LIMITED\",\"message\":\"Cok fazla istek, lutfen biraz sonra tekrar deneyin\"}");
        }
    }

    /** Render gibi ters proxy arkasindaki platformlarda gercek istemci IP'si X-Forwarded-For'da gelir. */
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

- [ ] **Step 6: Testi tekrar çalıştır, geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test -Dtest=RateLimitFilterTest`
Expected: 6 test PASS.

- [ ] **Step 7: `SecurityConfig`'e filtreyi ekle**

`backend/src/main/java/com/vetos/platform/security/SecurityConfig.java` — importlara ekle:
```java
import com.vetos.platform.web.RateLimitFilter;
```
`securityFilterChain` metodunun gövdesine, `JwtAuthenticationFilter` eklenen satırdan önce ekle:
```java
.addFilterBefore(new RateLimitFilter(), JwtAuthenticationFilter.class)
```
(Tam blok şu hale gelir:)
```java
.addFilterBefore(new RateLimitFilter(), JwtAuthenticationFilter.class)
.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);
```

- [ ] **Step 8: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS, `ApplicationModulesTest` dahil (yeni bağımlılık/sınıf modül sınırlarını bozmamalı — `RateLimitFilter` zaten `platform.web` içinde, `platform.security`'nin zaten kullandığı bir paket).

- [ ] **Step 9: Uç noktayı yerelde manuel doğrula**

Backend'i başlat: `cd backend && ./mvnw spring-boot:run` (arka planda)
Başka bir terminalde 11 kez art arda: `curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{}"`
Expected: ilk 10 istek `400`/`401` gibi normal bir hata kodu döner (login body'si boş olduğu için), 11. istek `429` döner.
Backend'i durdur.

- [ ] **Step 10: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/vetos/platform/web/RateLimitFilter.java \
  backend/src/main/java/com/vetos/platform/security/SecurityConfig.java \
  backend/src/test/java/com/vetos/platform/web/RateLimitFilterTest.java
git commit -m "feat: auth/public uc noktalarina IP bazli rate limiting ekle"
```

---

## Task 2: CORS — `localhost` Sadece Prod Dışında

**Files:**
- Modify: `backend/src/main/java/com/vetos/platform/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/vetos/platform/security/SecurityConfigCorsTest.java`

**Interfaces:**
- Consumes: yok (yeni).
- Produces: `SecurityConfig.corsConfigurationSource(Environment env)` — paket-içi görünürlük (test aynı pakette), eski `private corsConfigurationSource()` metodunun yerine geçer.

- [ ] **Step 1: Testi yaz (önce başarısız olacak şekilde)**

`backend/src/test/java/com/vetos/platform/security/SecurityConfigCorsTest.java`:
```java
package com.vetos.platform.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityConfigCorsTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private JsonAuthenticationEntryPoint authenticationEntryPoint;
    @Mock private JsonAccessDeniedHandler accessDeniedHandler;
    @Mock private Environment environment;

    private SecurityConfig aSecurityConfig() {
        SecurityConfig securityConfig = new SecurityConfig(jwtTokenProvider, authenticationEntryPoint, accessDeniedHandler);
        ReflectionTestUtils.setField(securityConfig, "vetlySiteOrigin", "https://vetly.example.com");
        ReflectionTestUtils.setField(securityConfig, "frontendBaseUrl", "https://app.vetly.example.com");
        return securityConfig;
    }

    private CorsConfiguration resolveConfig(CorsConfigurationSource source) {
        return ((UrlBasedCorsConfigurationSource) source).getCorsConfigurations().get("/**");
    }

    @Test
    void should_includeLocalhost_when_profileIsNotProd() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});

        CorsConfigurationSource source = aSecurityConfig().corsConfigurationSource(environment);

        assertThat(resolveConfig(source).getAllowedOriginPatterns()).contains("http://localhost:*");
    }

    @Test
    void should_excludeLocalhost_when_profileIsProd() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        CorsConfigurationSource source = aSecurityConfig().corsConfigurationSource(environment);

        assertThat(resolveConfig(source).getAllowedOriginPatterns()).doesNotContain("http://localhost:*");
    }

    @Test
    void should_alwaysIncludeConfiguredOrigins_regardlessOfProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        CorsConfigurationSource source = aSecurityConfig().corsConfigurationSource(environment);

        assertThat(resolveConfig(source).getAllowedOriginPatterns())
            .contains("https://vetly.example.com", "https://app.vetly.example.com");
    }
}
```

- [ ] **Step 2: Testi çalıştır, derleme hatasıyla başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q test -Dtest=SecurityConfigCorsTest`
Expected: derleme hatası — `corsConfigurationSource(Environment)` henüz yok (mevcut metot parametresiz).

- [ ] **Step 3: `SecurityConfig`'i güncelle**

`backend/src/main/java/com/vetos/platform/security/SecurityConfig.java` — importlara ekle:
```java
import org.springframework.core.env.Environment;
import java.util.ArrayList;
```
`securityFilterChain` metodunun imzasını değiştir:
```java
public SecurityFilterChain securityFilterChain(HttpSecurity http, Environment env) throws Exception {
```
İçindeki çağrıyı güncelle:
```java
.cors(cors -> cors.configurationSource(corsConfigurationSource(env)))
```
`corsConfigurationSource()` metodunu değiştir (imza + gövdenin ilk satırı):
```java
CorsConfigurationSource corsConfigurationSource(Environment env) {
    List<String> origins = new ArrayList<>(List.of(vetlySiteOrigin, frontendBaseUrl));
    if (!List.of(env.getActiveProfiles()).contains("prod")) {
        origins.add("http://localhost:*");
    }
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(origins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    // 201 Created yanitlarinda olusturulan kaynagin id'sini tasiyan Location
    // header'i, acikca expose edilmedikce tarayicida fetch()/JS'e HIC
    // gorunmez (CORS-safelisted response header degil) -- postForId (Yeni
    // Randevu, Muayene Baslat, Yeni Stok Kalemi, Kasa Ac, widget randevu
    // talebi) bu yuzden tarayicida sessizce basarisiz oluyordu.
    configuration.setExposedHeaders(List.of("Location"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```
(Metodun `private` niteleyicisi kaldırıldı — paket-içi görünürlük, testin erişebilmesi için. `origins` artık `List.of(...)` yerine değiştirilebilir `ArrayList` — `List.of("http://localhost:*", vetlySiteOrigin, frontendBaseUrl)` deseninden `new ArrayList<>(List.of(vetlySiteOrigin, frontendBaseUrl))` + koşullu `.add(...)`'a geçildi.)

- [ ] **Step 4: Testi tekrar çalıştır, geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test -Dtest=SecurityConfigCorsTest`
Expected: 3 test PASS.

- [ ] **Step 5: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vetos/platform/security/SecurityConfig.java \
  backend/src/test/java/com/vetos/platform/security/SecurityConfigCorsTest.java
git commit -m "fix: CORS'ta localhost sadece prod disi profillerde"
```

---

## Self-Review Notları (plan yazarı tarafından, kaydedilmeden önce)

- **Spec kapsaması:** §3 (rate limiting, sayılar/desen dahil) → Task 1. §4 (CORS/prod) → Task 2. §5 (test stratejisi) her iki görevin test adımlarına dağıtıldı. §6 (açık sorular) yok, spec zaten onaylı.
- **Yorum:** spec'in `AdvisoryLock`/`platform/concurrency` ile ilgisi yok — bu doküman farklı bir tur (Güvenlik Sertleştirme), yanlışlıkla karıştırılmadı.
- **Tip/imza tutarlılığı:** `RateLimitFilter` parametresiz constructor (spec ile aynı, Spring bean değil — `SecurityConfig` içinde `new` ile oluşturuluyor). `corsConfigurationSource(Environment env)` imzası Task 2'nin hem üretim kodunda hem testinde aynı.
- **Placeholder taraması:** yok, her adımda tam kod var.
