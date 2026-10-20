package com.vetos.platform.security;

import java.util.Optional;

/**
 * TARBIL eklentisi anahtarini dogrulayan port. platform/security bir modul
 * bagimliligi alamayacagi icin arayuz burada, uygulamasi integration/tarbil
 * modulunde (TarbilExtensionTokenAuthenticatorAdapter).
 */
public interface ExtensionTokenAuthenticator {
    /** clientVersion: ham X-Client-Version basligi (bos olabilir); son gorulen surum olarak kaydedilir. */
    Optional<AuthenticatedStaffUser> authenticate(String rawToken, String clientVersion);
}
