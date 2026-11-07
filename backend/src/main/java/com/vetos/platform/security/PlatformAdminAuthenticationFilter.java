package com.vetos.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JwtAuthenticationFilter'in paralel esdegeri. Kasitli olarak
 * TenantContext.set(...) ASLA cagrilmaz -- platform admin istekleri
 * hicbir kiraciya baglanmaz.
 */
@RequiredArgsConstructor
public class PlatformAdminAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final PlatformAdminJwtTokenProvider platformAdminJwtTokenProvider;

    /** Giris/cikis kimlik dogrulamasi istemez; eski bir cerez girisi CSRF kontrolune takmasin. */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith(request.getContextPath() + "/api/v1/platform-admin/auth/");
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String header = request.getHeader(AUTH_HEADER);
            boolean fromHeader = header != null && header.startsWith(BEARER_PREFIX);
            String token = fromHeader ? header.substring(BEARER_PREFIX.length()) : SessionCookies.read(request, SessionCookies.ADMIN_NAME);
            if (token != null && !fromHeader && SessionCookies.failsCsrfCheck(request)) {
                SessionCookies.rejectCsrf(response);
                return;
            }
            if (token != null) {
                try {
                    AuthenticatedPlatformAdmin principal = platformAdminJwtTokenProvider.parse(token);
                    var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ex) {
                    // Gecersiz/suresi dolmus/yanlis turde token: kimlik dogrulanmamis
                    // olarak devam et, korumali endpoint'lerde Spring Security 401 doner.
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
