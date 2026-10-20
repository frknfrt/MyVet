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
