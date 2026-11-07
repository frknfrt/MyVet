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

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final StaffSessionRevocations revocations;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Herkese acik uclar ve giris kiraci baglami olmadan (root oturum) calismali: tarayici baska bir klinige girisliyken
     * acilan davet linki o klinigin filtresine takilmasin (spec 2026-09-17 S11).
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        String contextPath = request.getContextPath();
        String path = contextPath != null && uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
        return path.startsWith("/api/v1/public/") || path.startsWith("/api/v1/auth/");
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
            String token = fromHeader ? header.substring(BEARER_PREFIX.length()) : SessionCookies.read(request, SessionCookies.NAME);
            if (token != null && !fromHeader && SessionCookies.failsCsrfCheck(request)) {
                // Cerezle kimlik dogrulamasi + veri degistiren istek + uygulamanin basligi yok: baska bir sayfadan
                // (or. ayni site sayilan bir alt alan adindan) gonderilmis olabilir. Islem yapmadan reddet.
                SessionCookies.rejectCsrf(response);
                return;
            }
            if (token != null) {
                try {
                    AuthenticatedStaffUser principal = jwtTokenProvider.parse(token);
                    if (revocations.revoked(principal.staffUserId(), principal.issuedMillis())) {
                        throw new io.jsonwebtoken.JwtException("Session revoked");
                    }
                    var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    TenantContext.set(principal.tenantId());
                    MDC.put("tenantId", principal.tenantId().toString());
                } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ex) {
                    // Gecersiz/suresi dolmus token: kimlik dogrulanmamis olarak devam et,
                    // korumali endpoint'lerde Spring Security 401 doner.
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
            SecurityContextHolder.clearContext();
        }
    }
}
