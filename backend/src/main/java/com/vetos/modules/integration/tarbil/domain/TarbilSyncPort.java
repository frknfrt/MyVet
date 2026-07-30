package com.vetos.modules.integration.tarbil.domain;

/**
 * @docs/architecture.md Bolum 3 (Open/Closed) ile ayni desen: bugun tek
 * implementasyon var (MockTarbilAdapter -- gercek Bakanlik API kimlik
 * bilgileri/entegrasyon dokumani olmadan gercek cagri yapilamiyor), yarin
 * gercek HTTP adaptoru eklenecegi zaman AiGatewayRouter'daki gibi tek
 * yapilan yeni bir @Component sinifi yazmak olacak -- bu arayuzu ya da
 * cagiran kodu degistirmeye gerek kalmayacak.
 */
public interface TarbilSyncPort {
    TarbilSyncOutcome sync(TarbilSyncRequest request);
}
