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
