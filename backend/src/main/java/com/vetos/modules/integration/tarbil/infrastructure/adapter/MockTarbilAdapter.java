package com.vetos.modules.integration.tarbil.infrastructure.adapter;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncOutcome;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncPort;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * T.C. Tarim ve Orman Bakanligi TARBIL sistemine gercek baglanti icin
 * kimlik bilgisi/entegrasyon dokumani bu ortamda mevcut degil. Bu adapter,
 * gercek HTTP client'i (@docs/architecture.md Bolum 3 desenindeki gibi)
 * degistirene kadar cagiran kodu (QueueTarbilSyncUseCase, event
 * listener'lar) hic etkilemeden yerini alan bir stub'tir -- basari/hata
 * davranisini simule eder ki senkron ekrani (Ayarlar > Entegrasyonlar)
 * gercekci bicimde test edilebilsin.
 */
@Component
@Slf4j
class MockTarbilAdapter implements TarbilSyncPort {

    private static final double SIMULATED_FAILURE_RATE = 0.1;

    @Override
    public TarbilSyncOutcome sync(TarbilSyncRequest request) {
        log.info("TARBIL senkron (mock): patientId={}, tur={}", request.patientId(), request.syncType());

        if (Math.random() < SIMULATED_FAILURE_RATE) {
            return TarbilSyncOutcome.failure("TARBIL servisi gecici olarak yanit vermedi (simule edilmis hata)");
        }
        return TarbilSyncOutcome.success("Kabul edildi");
    }
}
