package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.domain.TarbilSourceKind;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    private final TarbilSubmissionRepository tarbilSyncLogRepository;

    @Transactional
    public UUID queueVaccination(UUID patientId, UUID vaccinationRecordId) {
        // Cagiran: VaccinationRecordedEventListener -- kimligi dogrulanmis bir istek
        // icindeki senkron @EventListener, TenantContext kurulu.
        Optional<TarbilSubmission> existing = tarbilSyncLogRepository.findByDocumentTypeAndSourceId(TarbilDocumentType.VACCINATION, vaccinationRecordId);
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        return tarbilSyncLogRepository.save(
            TarbilSubmission.queueVaccination(TenantContext.current(), patientId, vaccinationRecordId)
        ).getId();
    }

    /** Stoktan verilen kalemli recete (spec 2026-10-09 S5.6). Idempotent. */
    @Transactional
    public UUID queuePrescription(UUID prescriptionId, UUID patientId, UUID tarbilDiseaseId) {
        return tarbilSyncLogRepository.findByDocumentTypeAndSourceId(TarbilDocumentType.PRESCRIPTION, prescriptionId)
            .map(TarbilSubmission::getId)
            .orElseGet(() -> tarbilSyncLogRepository.save(TarbilSubmission.queuePrescription(
                TenantContext.current(), patientId, TarbilSourceKind.PRESCRIPTION, prescriptionId, tarbilDiseaseId)).getId());
    }

    /** Iptal edilen recetenin bekleyen aktarimi reddedilir; gonderilmis aktarima dokunulmaz (TARBIL'de iptal hekimde). */
    @Transactional
    public void dismissPrescription(UUID prescriptionId, String reason) {
        tarbilSyncLogRepository.findByDocumentTypeAndSourceId(TarbilDocumentType.PRESCRIPTION, prescriptionId)
            .filter(s -> s.getTenantId().equals(TenantContext.current()))
            .filter(s -> s.getStatus() == TarbilSyncStatus.PENDING)
            .ifPresent(s -> {
                s.dismiss(null, reason, Instant.now());
                tarbilSyncLogRepository.save(s);
            });
    }
}

