package com.vetos.platform.http;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Dis servislere giden RestClient'lar icin tek fabrika. RestClient.create()
 * HICBIR zaman asimi koymaz: baglantiyi kabul edip cevap yazmayan bir
 * saglayici (iyzico, Meta, Ileti Merkezi) cagiran thread'i suresiz bekletir --
 * HTTP isteginde kullaniciyi, @Async outbox'ta tum executor havuzunu kilitler.
 * Her adapter kendi baglanti/okuma sinirini acikca vermek zorundadir.
 */
public final class OutboundHttp {

    private OutboundHttp() {
    }

    public static RestClient restClient(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
