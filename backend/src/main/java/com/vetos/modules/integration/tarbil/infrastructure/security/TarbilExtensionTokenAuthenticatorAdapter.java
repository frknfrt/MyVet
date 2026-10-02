package com.vetos.modules.integration.tarbil.infrastructure.security;

import com.vetos.modules.integration.tarbil.application.AuthenticateExtensionTokenUseCase;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.security.ExtensionTokenAuthenticationFilter;
import com.vetos.platform.security.ExtensionTokenAuthenticator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class TarbilExtensionTokenAuthenticatorAdapter implements ExtensionTokenAuthenticator {

    private final AuthenticateExtensionTokenUseCase authenticateExtensionTokenUseCase;

    @Override
    public Optional<AuthenticatedStaffUser> authenticate(String rawToken) {
        return authenticateExtensionTokenUseCase.execute(rawToken).map(identity -> new AuthenticatedStaffUser(
            identity.staffUserId(), identity.tenantId(), List.of(), ExtensionTokenAuthenticationFilter.ROLE
        ));
    }
}
