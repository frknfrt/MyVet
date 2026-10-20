package com.vetos.platform.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vetos.platform.ratelimit.RateLimitResponses;
import com.vetos.platform.ratelimit.TenantRateLimiter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Sadece kimlik dogrulama gerektirmeyen uc noktalar icin (auth + public) --
 * kimlik dogrulanmis trafik bu filtreden gecmez.
 * Kimlik dogrulanmis trafik icin bkz. TenantRateLimitFilter.
 * Bellek-ici (per-instance) -- coklu instance'ta limit instance sayisiyla
 * orantili gevser, kabul edilebilir (bkz. tasarimin bagimlilik notu).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    private static final long DEFAULT_MAX_TRACKED_CLIENTS = 100_000;

    private final Cache<String, Bucket> authBuckets;
    private final Cache<String, Bucket> publicBuckets;
    private final Cache<String, Bucket> platformAdminAuthBuckets;
    private final Cache<String, Bucket> extensionPairBuckets;

    public RateLimitFilter() {
        this(DEFAULT_MAX_TRACKED_CLIENTS);
    }

    /** Test icin kucuk ust sinir. Tahliye senkron (executor = calling thread) -- olcum deterministik. */
    RateLimitFilter(long maxTrackedClientsPerPool) {
        this.authBuckets = newCache(maxTrackedClientsPerPool);
        this.publicBuckets = newCache(maxTrackedClientsPerPool);
        this.platformAdminAuthBuckets = newCache(maxTrackedClientsPerPool);
        this.extensionPairBuckets = newCache(maxTrackedClientsPerPool);
    }

    private static Cache<String, Bucket> newCache(long maximumSize) {
        // 10 dk kullanilmayan IP kaydi silinir; sahte IP selinde bellek sinirsiz buyumez.
        return Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(maximumSize)
            .executor(Runnable::run)
            .build();
    }

    long trackedClientCount() {
        long total = 0;
        for (Cache<String, Bucket> cache : List.of(authBuckets, publicBuckets, platformAdminAuthBuckets, extensionPairBuckets)) {
            cache.cleanUp();
            total += cache.estimatedSize();
        }
        return total;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {
        // getRequestURI() ham (decode edilmemis) degeri dondurur; Spring Security/MVC ise
        // decode edilmis path uzerinden eslesme yapar. Percent-encoding ile (orn. "/api/v1/%61uth/login")
        // bu filtreyi bypass edip gercek handler'a ulasmayi engellemek icin decode edilmis,
        // uygulama-goreli path kullanilir.
        String path = PATH_HELPER.getPathWithinApplication(request);
        Cache<String, Bucket> buckets;
        Bandwidth limit;
        if (path.startsWith("/api/v1/auth/")) {
            buckets = authBuckets;
            limit = Bandwidth.builder().capacity(10).refillIntervally(10, Duration.ofMinutes(5)).build();
        } else if (path.startsWith("/api/v1/platform-admin/auth/")) {
            // Platform superadmin girisi -- en yuksek yetkili yuzey, normal personel
            // girisiyle ayni siki limit (5dk'da 10) ama AYRI bir bucket havuzunda tutulur
            // (paylasilan authBuckets kullanilsaydi, iki farkli login sistemine ayni IP'den
            // gelen trafik birbirinin limitini etkilerdi).
            buckets = platformAdminAuthBuckets;
            limit = Bandwidth.builder().capacity(10).refillIntervally(10, Duration.ofMinutes(5)).build();
        } else if (path.equals("/api/v1/tarbil-extension/pair")) {
            // TARBIL eklentisi eslestirme kodu deneme saldirisina karsi -- personel girisi
            // kadar siki (5dk'da 10), ayri havuzda.
            buckets = extensionPairBuckets;
            limit = Bandwidth.builder().capacity(10).refillIntervally(10, Duration.ofMinutes(5)).build();
        } else if (path.startsWith("/api/v1/public/")) {
            buckets = publicBuckets;
            limit = Bandwidth.builder().capacity(60).refillIntervally(60, Duration.ofMinutes(1)).build();
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        // getRemoteAddr(): prod'da SERVER_FORWARD_HEADERS_STRATEGY=native ile Tomcat RemoteIpValve,
        // guvenilir proxy'den (127.0.0.1, nginx) gelen X-Forwarded-For'daki istemci IP'sini buraya
        // yansitir; nginx header'i $remote_addr ile UZERINE yazar (bkz. docs/deployment.md). Header'i
        // burada elle okumak sahteciligi geri getirir -- yapma.
        String clientIp = request.getRemoteAddr();
        Bucket bucket = buckets.get(clientIp, ip -> Bucket.builder().addLimit(limit).build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
        } else {
            RateLimitResponses.write(response, TenantRateLimiter.toRetrySeconds(probe.getNanosToWaitForRefill()), path);
        }
    }
}
