package com.vetos.platform.resilience;

import com.vetos.platform.observability.IntegrationOutcomes;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Predicate;

/**
 * Devre kesici "hata" ve okuma retry'i "tekrar dene" karari (spec 2026-10-16 §4.2): yalniz saglayici
 * kesintisi -- baglanti/zaman asimi veya IntegrationOutcomes.isOutageStatus durumlari. Bizim taraftaki
 * 4xx, ayristirma hatasi ve acik devre (CallNotPermittedException) sayilmaz. yml'de sinif adiyla
 * baglanir (record-failure-predicate / retry-exception-predicate) -- public bos kurucu sart.
 */
public class OutageFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        return isOutage(throwable);
    }

    public static boolean isOutage(Throwable throwable) {
        if (throwable instanceof RestClientResponseException e) {
            return IntegrationOutcomes.isOutageStatus(e.getStatusCode().value());
        }
        return throwable instanceof ResourceAccessException;
    }
}
