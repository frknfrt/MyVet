package com.vetos.platform.security;

import java.util.Optional;

/**
 * TARBIL eklentisi anahtarini dogrulayan port. platform/security bir modul
 * bagimliligi alamayacagi icin arayuz burada, uygulamasi integration/tarbil
 * modulunde (TarbilExtensionTokenAuthenticatorAdapter).
 */
public interface ExtensionTokenAuthenticator {
    Optional<AuthenticatedStaffUser> authenticate(String rawToken);
}
