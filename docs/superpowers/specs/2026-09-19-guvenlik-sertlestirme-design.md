# Güvenlik Sertleştirme — Tasarım Dokümanı

**Tarih:** 2026-09-19
**Durum:** Tasarım onaylandı, implementasyon bekliyor
**İlgili modül:** `platform/security`, `platform/web`

## 1. Bağlam ve Amaç

`SecurityConfig` ve 7 herkese açık (`permitAll`) controller'ı (`PublicPatientIntakeController`, `PublicAppointmentController`, `PublicSignupController`, `PublicClinicController`, `PublicStaffInviteController`, `PublicPaymentCallbackController`, `FaturaEntegratorCallbackController`) inceledim. İki somut bulgu:

1. **Hiçbir rate limiting yok.** `/api/v1/auth/**` (personel girişi) ve `/api/v1/public/**` (hasta kayıt widget'ı, randevu talebi, kayıt, callback'ler) tamamen `permitAll` ve sınırsız — kaba kuvvet (brute-force) login denemesi ya da widget'ların spam ile doldurulması hiçbir şekilde engellenmiyor.
2. **CORS'ta `http://localhost:*`, prod'da bile güvenilir origin listesinde.** `vetlySiteOrigin`/`frontendBaseUrl` prod'da zorunlu env değişkenleriyle geliyor (doğru) ama `http://localhost:*` deseni Java kodunda sabit, profile'dan bağımsız — yani prod'da bile, `allowCredentials(true)` ile birlikte, kendi bilgisayarında localhost'ta bir sayfa çalıştıran biri prod API'ye kimlik bilgili (credentialed) istek atabilir.

**Doğrulanan, sorun OLMAYAN bir nokta:** `PublicPaymentCallbackController` (iyzico) callback body'sindeki `token`'a güvenmiyor — gelen isteği sadece tetikleyici olarak kullanıp `paymentGatewayPort.retrieveCheckoutResult(token)` ile iyzico'nun kendi API'sine sunucu-sunucu doğrulaması yapıyor (e-Fatura callback'iyle aynı, doğru desen). Ayrı bir imza doğrulaması eklemeye gerek yok.

## 2. Kapsam

**Bu turda yapılacak:**
- IP bazlı rate limiting — `/api/v1/auth/**` ve `/api/v1/public/**` için, iki farklı sıkılıkta.
- CORS'ta `http://localhost:*`'ın sadece prod dışı profillerde eklenmesi.

**Kapsam dışı (bilinçli olarak):**
- **Güvenlik başlıkları (CSP, HSTS vb.)** — genelde barındırma platformunun TLS/proxy katmanında ele alınır, platform netleşmeden anlamlı değil.
- **Bağımlılık güvenlik taraması (Dependabot vb.)** — CI/CD dokümanına eklenebilecek ayrı, küçük bir madde; bu turda değil.
- **JWT secret güç kontrolü** — `application-prod.yml` zaten `JWT_SECRET`'ı zorunlu kılıyor; HS256 imzalama kütüphanesi (jjwt) zaten çok kısa anahtarları reddediyor (kütüphane seviyesinde zaten korunuyor, ek koda gerek yok).

## 3. Rate Limiting

`com.bucket4j:bucket4j-core` eklenir (Redis gerekmiyor — bellek-içi, tek/az sayıda instance için yeterli; B ve C'deki gibi ileride çoklu instance yoğun trafik olursa Redis-backed bir sürüme geçmek `RateLimitFilter`'ın içini değiştirmekten ibaret, çağıran kod etkilenmez).

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

`SecurityConfig`'e eklenir (`RequestIdFilter`'dan sonra, `JwtAuthenticationFilter`'dan önce — sıralama önemli değil, ikisi de auth'tan önce):
```java
.addFilterBefore(new RateLimitFilter(), JwtAuthenticationFilter.class)
```

Sayılar (auth: 5dk'da 10 deneme, public: 1dk'da 60 istek) makul başlangıç değerleri — gerçek trafiğe göre ayarlanabilir.

## 4. CORS — `localhost` Sadece Prod Dışında

```java
private CorsConfigurationSource corsConfigurationSource(Environment env) {
    List<String> origins = new java.util.ArrayList<>(List.of(vetlySiteOrigin, frontendBaseUrl));
    if (!List.of(env.getActiveProfiles()).contains("prod")) {
        origins.add("http://localhost:*");
    }
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(origins);
    // ... geri kalani degismiyor
}
```
`Environment`, `securityFilterChain(HttpSecurity http, Environment env)` metoduna parametre olarak enjekte edilir (Spring bunu otomatik sağlar).

## 5. Test Stratejisi

- `RateLimitFilter` — birim test: limit içindeki isteklerin geçtiği, limiti aşan isteğin 429 döndüğü, `X-Forwarded-For` varsa onun kullanıldığı, farklı IP'lerin birbirini etkilemediği (mock `HttpServletRequest`/`FilterChain`).
- CORS — `corsConfigurationSource`'un `prod` profilinde `localhost` deseni içermediğini, diğer profillerde içerdiğini doğrulayan birim test (mock `Environment`).
- `ApplicationModulesTest` — yeni sınıfların modül sınırlarını bozmadığının doğrulanması.

## 6. Açık Sorular

Yok — tasarım kullanıcı onayından geçti.
