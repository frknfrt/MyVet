package com.vetos.platform.security;

import com.vetos.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionTokenAuthenticationFilterTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    @Test
    void should_setPrincipalAndTenant_when_tokenValid() throws Exception {
        ExtensionTokenAuthenticator authenticator = raw -> "vtx_ok".equals(raw)
            ? Optional.of(new AuthenticatedStaffUser(staffId, tenantId, List.of(), "TARBIL_EXTENSION"))
            : Optional.empty();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer vtx_ok");
        AtomicReference<Authentication> seenAuth = new AtomicReference<>();
        AtomicReference<UUID> seenTenant = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            seenAuth.set(SecurityContextHolder.getContext().getAuthentication());
            seenTenant.set(TenantContext.currentOrNull());
        };

        new ExtensionTokenAuthenticationFilter(authenticator).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seenAuth.get()).isNotNull();
        assertThat(seenAuth.get().getAuthorities()).extracting(Object::toString).containsExactly("ROLE_TARBIL_EXTENSION");
        assertThat(seenTenant.get()).isEqualTo(tenantId);
        assertThat(TenantContext.currentOrNull()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void should_returnUnauthenticated_when_tokenRevoked() throws Exception {
        ExtensionTokenAuthenticator authenticator = raw -> Optional.empty();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer vtx_revoked");
        AtomicReference<Authentication> seenAuth = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenAuth.set(SecurityContextHolder.getContext().getAuthentication());

        new ExtensionTokenAuthenticationFilter(authenticator).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seenAuth.get()).isNull();
    }

    @Test
    void should_ignoreJwtShapedBearer() throws Exception {
        ExtensionTokenAuthenticator authenticator = raw -> {
            throw new AssertionError("JWT bir eklenti anahtari olarak denenmemeli");
        };
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.x.y");
        AtomicReference<Authentication> seenAuth = new AtomicReference<>();

        new ExtensionTokenAuthenticationFilter(authenticator)
            .doFilter(request, new MockHttpServletResponse(), (req, res) -> seenAuth.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(seenAuth.get()).isNull();
    }
}
