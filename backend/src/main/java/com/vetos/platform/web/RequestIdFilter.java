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
 * uc noktalar, rate-limit'e takilan istekler) de dahil HER istek bir
 * requestId alir. JwtAuthenticationFilter (bu filtreden SONRA calisir)
 * kimlik dogrulanirsa ayrica tenantId'yi MDC'ye ekler.
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
