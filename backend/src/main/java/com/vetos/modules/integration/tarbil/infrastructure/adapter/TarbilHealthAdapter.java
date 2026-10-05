package com.vetos.modules.integration.tarbil.infrastructure.adapter;

import com.vetos.modules.integration.tarbil.domain.FailedTarbilSyncView;
import com.vetos.modules.integration.tarbil.domain.TarbilHealthPort;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Platform admin saglik paneli (eklenti modeli, 2026-10-05): sunucu TARBIL'e kendisi gondermedigi icin "basarisiz
 * senkron" yoktur; takilmis sayilan, STALE_AFTER'dan uzun suredir eklentiden gonderilmeyen bekleyen aktarimlardir.
 */
@Component
@RequiredArgsConstructor
class TarbilHealthAdapter implements TarbilHealthPort {

    static final Duration STALE_AFTER = Duration.ofDays(3);

    private final TarbilSubmissionRepository submissions;
    private final TenantLookupPort tenantLookupPort;
    private final PatientLookupPort patientLookupPort;

    @Override
    @Transactional(readOnly = true)
    public List<FailedTarbilSyncView> findRecentFailed(int limit) {
        return submissions.findPendingQueuedBefore(Instant.now().minus(STALE_AFTER), limit).stream()
            .map(this::toView)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countFailedForTenant(UUID tenantId) {
        return submissions.countPendingQueuedBefore(tenantId, Instant.now().minus(STALE_AFTER));
    }

    private FailedTarbilSyncView toView(TarbilSubmission s) {
        String tenantName = tenantLookupPort.findTenantName(s.getTenantId()).orElse("Bilinmeyen Klinik");
        String patientName = "—";
        if (s.getPatientId() != null) {
            // Hasta silinmis/erisilemez olabilir -- sayfanin tamami bu yuzden bozulmamali.
            try {
                patientName = patientLookupPort.findSummaryById(s.getPatientId()).name();
            } catch (RuntimeException ex) {
                patientName = "Bilinmeyen Hasta";
            }
        }
        long days = Duration.between(s.getQueuedAt(), Instant.now()).toDays();
        return new FailedTarbilSyncView(
            s.getId(), s.getTenantId(), tenantName, s.getPatientId(), patientName, s.getDocumentType().name(),
            days + " gündür klinikteki eklentiden TARBİL'e gönderilmedi", 0, s.getQueuedAt()
        );
    }
}
