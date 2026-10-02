package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Asiyi "TARBIL'e aktarilmayi bekliyor" olarak kuyruga ekler. Disariya hicbir sey
 * gondermez. Ayni asi icin ikinci cagri (kayit + sonradan "yapildi") yeni satir
 * olusturmaz.
 */
@Service
@RequiredArgsConstructor
public class QueueTarbilSyncUseCase {

    private final TarbilSyncLogRepository tarbilSyncLogRepository;

    @Transactional
    public UUID queueVaccination(UUID patientId, UUID vaccinationRecordId) {
        // Cagiran: VaccinationRecordedEventListener -- kimligi dogrulanmis bir istek
        // icindeki senkron @EventListener, TenantContext kurulu.
        Optional<TarbilSyncLog> existing = tarbilSyncLogRepository.findByVaccinationRecordId(vaccinationRecordId);
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        return tarbilSyncLogRepository.save(
            TarbilSyncLog.queueVaccination(TenantContext.current(), patientId, vaccinationRecordId)
        ).getId();
    }
}
