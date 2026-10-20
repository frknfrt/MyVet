package com.vetos.platform.audit;

import com.vetos.platform.security.AuthenticatedPlatformAdmin;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.security.ExtensionTokenAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditActorResolverTest {

    private final AuditActorResolver resolver = new AuditActorResolver();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID STAFF = UUID.randomUUID();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(Object principal, String role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void should_resolveStaffViaWeb() {
        authenticate(new AuthenticatedStaffUser(STAFF, TENANT, List.of(), "VET"), "VET");
        AuditActor actor = resolver.current();
        assertThat(actor).isEqualTo(new AuditActor(AuditActor.ActorType.STAFF, STAFF, null, "VET", null, AuditActor.Channel.WEB));
    }

    @Test
    void should_markExtensionChannel() {
        authenticate(new AuthenticatedStaffUser(STAFF, TENANT, List.of(), "VET"), ExtensionTokenAuthenticationFilter.ROLE);
        assertThat(resolver.current().channel()).isEqualTo(AuditActor.Channel.EXTENSION);
    }

    @Test
    void should_resolvePlatformAdmin() {
        UUID adminId = UUID.randomUUID();
        authenticate(new AuthenticatedPlatformAdmin(adminId, "furkan@vetly.com.tr"), "PLATFORM_ADMIN");
        assertThat(resolver.current()).isEqualTo(AuditActor.platformAdmin(adminId, "furkan@vetly.com.tr"));
    }

    @Test
    void should_carryImpersonator() {
        authenticate(new AuthenticatedStaffUser(STAFF, TENANT, List.of(), "ADMIN", "destek@vetly.com.tr"), "ADMIN");
        assertThat(resolver.current().impersonatedBy()).isEqualTo("destek@vetly.com.tr");
    }

    @Test
    void should_throw_when_noAuthentication() {
        assertThatThrownBy(resolver::current).isInstanceOf(IllegalStateException.class);
    }
}
