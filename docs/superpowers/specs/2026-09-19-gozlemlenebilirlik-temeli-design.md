# Gözlemlenebilirlik Temeli — Tasarım Dokümanı

**Tarih:** 2026-09-19
**Durum:** Tasarım onaylandı, implementasyon bekliyor
**İlgili modül:** `platform/web` (yeni dosyalar), `platform/security`, `backend/src/main/resources/application*.yml`

## 1. Bağlam ve Amaç

Loglama zaten var (`GlobalExceptionHandler.handleUnexpected`, beklenmeyen her hatayı stack trace'iyle `ERROR` seviyesinde logluyor) ama iki gerçek eksik var:

1. **İzlenebilirlik yok:** Bir log satırı hangi isteğe, hangi kiracıya ait — bilmenin yolu yok. Yoğun bir günde 5 farklı isteğin logları iç içe geçtiğinde, hangi hatanın hangi kullanıcıya ait olduğunu ayırt etmek pratikte imkansız.
2. **Kimse izlemiyor:** Hatalar Render'ın (ya da her neresi olursa) log akışında sessizce duruyor — aktif olarak biri bakmadıkça fark edilmiyor. Hata izleme servisi (Sentry vb.) yok.
3. **`logging.level.com.vetos: debug`, prod profilinde bile geçerli** — `application-prod.yml`'de bir override yok. Prod'da `debug` seviyesi gereksiz gürültü/log hacmi/maliyet ve potansiyel hassas veri sızıntısı riski taşır (bu dosyanın kendi felsefesiyle — "prod'da güvensiz varsayılanların üstüne açıkça yazılır" — çelişiyor).

**Kullanıcıyla netleşen kapsam kararı:** Hata izleme servisi (Sentry vb.) **henüz belli değil** — B (deploy platformu) ve D (nesne depolama) ile aynı durum. Bu tur, hangi servis seçilirse seçilsin değişmeyecek kısma odaklanıyor: izlenebilir loglama + prod log seviyesi düzeltmesi + metrik uç noktalarının açılması. Gerçek hata-izleme entegrasyonu, servis netleşince küçük bir ek olacak.

## 2. Kapsam

**Bu turda yapılacak:**
- `RequestIdFilter` — her isteğe bir `requestId` atayıp MDC'ye yazan, güvenlik filtre zincirinin en başında çalışan yeni bir filtre.
- `JwtAuthenticationFilter`'a tek satır ekleme — zaten `TenantContext.set(...)` yaptığı yerde, aynı değeri MDC'ye de yazması.
- Konsol log formatına `requestId`/`tenantId`'yi ekleyen bir `application.yml` ayarı.
- `application-prod.yml`'e `logging.level.com.vetos: info` override'ı.
- Actuator metrik uç noktalarının açılması (Bildirim/CI-CD dokümanında eklenen Actuator'a ek — bkz. §5).

**Kapsam dışı (bilinçli olarak):**
- **Gerçek hata izleme servisi entegrasyonu (Sentry vb.)** — servis netleşmeden yazılamaz. Not: Sentry'nin Spring Boot starter'ı tek bağımlılık + bir DSN env değişkeni kadar küçük bir ekleme — servis seçilince bu gerçekten küçük bir tur olur.
- **Dashboard/alarm kurulumu (Grafana, vb.)** — platformdan bağımsız değil, bu turun dışında.
- **Log toplama/gönderme altyapısı (log shipping)** — barındırma platformu genelde kendi log akışını zaten sağlıyor (Render dahil); ayrı bir log toplayıcı, platform netleşmeden tasarlanamaz.

## 3. İzlenebilir Loglama

`platform/web/RequestIdFilter.java` (yeni):
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

/**
 * Guvenlik zincirinin EN BASINDA calisir (kimlik dogrulamadan once) --
 * kimlik dogrulanmamis istekler (basarisiz login denemeleri, herkese acik
 * uc noktalar) de dahil HER istek bir requestId alir. JwtAuthenticationFilter
 * (bu filtreden SONRA calisir) kimlik dogrulanirsa ayrica tenantId'yi
 * MDC'ye ekler.
 */
public class RequestIdFilter extends OncePerRequestFilter {
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {
        MDC.put(MDC_KEY, UUID.randomUUID().toString());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```

`SecurityConfig` — `JwtAuthenticationFilter`'dan önce zincire eklenir:
```java
.addFilterBefore(new RequestIdFilter(), JwtAuthenticationFilter.class)
.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
```

`JwtAuthenticationFilter.doFilterInternal` — mevcut `TenantContext.set(principal.tenantId())` satırının yanına:
```java
TenantContext.set(principal.tenantId());
org.slf4j.MDC.put("tenantId", principal.tenantId().toString());
```
ve mevcut `finally` bloğundaki `TenantContext.clear()`'ın yanına `MDC.remove("tenantId")`.

`application.yml` — konsol log formatına MDC değerlerini ekleyen ayar:
```yaml
logging:
  pattern:
    console: "%d{HH:mm:ss.SSS} %-5level [%X{requestId:-etc.}/%X{tenantId:-sistem}] %logger{36} - %msg%n"
```
`:-etc.`/`:-sistem` varsayılanları — MDC boşsa (örn. `@Scheduled` işler, `TenantContext` köprülenmemiş arka plan kodu) log satırı boş parantez yerine anlamlı bir işaretleyici gösterir.

## 4. Prod Log Seviyesi

`application-prod.yml`'e (dosyanın kendi felsefesiyle tam tutarlı — bkz. dosyanın başındaki açıklama):
```yaml
logging:
  level:
    com.vetos: info
```

## 5. Metrik Uç Noktaları

CI/CD dokümanında (`2026-09-18-dagitim-ci-cd-design.md` §4) eklenmesi planlanan `spring-boot-starter-actuator`'a ek olarak `micrometer-registry-prometheus` bağımlılığı eklenir — bu, hangi platforma gidilirse gidilsin (Prometheus, Grafana Cloud, Datadog'un çoğu Prometheus formatını okuyabiliyor) evrensel olarak kazınabilir (scrape edilebilir) bir format. `application.yml`'deki `management.endpoints.web.exposure.include` listesi `health`'ten `health,metrics,prometheus`'a genişletilir. Bu turda hiçbir dashboard/alarm kurulmuyor — sadece uç nokta hazır bekliyor.

**Not:** Bu bölüm, CI/CD dokümanının Actuator eklemesine bağımlı — implementasyon sırasında iki dokümanın ilgili görevleri birlikte veya CI/CD'ninki önce olacak şekilde sıralanmalı.

## 6. Test Stratejisi

- `RequestIdFilter` — birim test: her istekte MDC'ye bir `requestId` yazıldığı ve `finally`'de temizlendiği (mock `FilterChain` ile).
- `JwtAuthenticationFilter` — mevcut testlerine (varsa) ek: geçerli bir token ile isteğin `tenantId`'yi MDC'ye yazdığı, istek bitince temizlendiği.
- Manuel doğrulama (implementasyon sırasında, birim test kapsamına girmiyor): yerel ortamda bir istek atılıp konsol log satırında `requestId`/`tenantId`'nin göründüğü gözle doğrulanır.
- `ApplicationModulesTest` — yeni `platform/web` sınıflarının modül sınırlarını bozmadığının doğrulanması.

## 7. Açık Sorular

Yok — tasarım kullanıcı onayından geçti. Hata izleme servisi netleştiğinde §2'deki kapsam dışı madde ayrı, küçük bir tasarım turu olacak.
