package com.vetos.platform.security;

import com.vetos.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JwtAuthenticationFilter'in eklenti anahtari esdegeri. Yalnizca "vtx_" onekli
 * anahtarlari dener -- JWT burada kimlik dogrulamaz, eklenti anahtari da ana
 * zincirde dogrulanmaz (iki zincir birbirine kapali).
 */
@RequiredArgsConstructor
public class ExtensionTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_PREFIX = "vtx_";
    public static final String ROLE = "TARBIL_EXTENSION";

    private final ExtensionTokenAuthenticator authenticator;

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith(BEARER_PREFIX + TOKEN_PREFIX)) {
                authenticator.authenticate(header.substring(BEARER_PREFIX.length())).ifPresent(principal -> {
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))
                    ));
                    TenantContext.set(principal.tenantId());
                    MDC.put("tenantId", principal.tenantId().toString());
                });
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
            SecurityContextHolder.clearContext();
        }
    }
}
