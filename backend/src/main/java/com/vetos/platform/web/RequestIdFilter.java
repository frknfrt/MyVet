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
