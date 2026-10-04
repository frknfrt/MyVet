package com.vetos.modules.integration.tarbil.infrastructure.adapter;

import com.vetos.modules.integration.tarbil.domain.FailedTarbilSyncView;
import com.vetos.modules.integration.tarbil.domain.TarbilHealthPort;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilHealthAdapter implements TarbilHealthPort {

    private final TarbilSyncLogRepository tarbilSyncLogRepository;
    private final TenantLookupPort tenantLookupPort;
    private final PatientLookupPort patientLookupPort;

    @Override
    @Transactional(readOnly = true)
    public List<FailedTarbilSyncView> findRecentFailed(int limit) {
        return tarbilSyncLogRepository.findRecentByStatus(TarbilSyncStatus.FAILED, limit).stream()
            .map(this::toView)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countFailedForTenant(UUID tenantId) {
        return tarbilSyncLogRepository.findByTenantId(tenantId).stream()
            .filter(l -> l.getStatus() == TarbilSyncStatus.FAILED)
            .count();
    }

    private FailedTarbilSyncView toView(TarbilSyncLog log) {
        String tenantName = tenantLookupPort.findTenantName(log.getTenantId()).orElse("Bilinmeyen Klinik");
        // Hasta silinmis/erisilemez olabilir -- sayfanin tamami bu yuzden bozulmamali.
        String patientName;
        try {
            patientName = patientLookupPort.findSummaryById(log.getPatientId()).name();
        } catch (RuntimeException ex) {
            patientName = "Bilinmeyen Hasta";
        }
        return new FailedTarbilSyncView(
            log.getId(), log.getTenantId(), tenantName, log.getPatientId(), patientName,
            log.getSyncType(), log.getFailureReason(), log.getAttemptCount(), log.getAttemptedAt()
        );
    }
}
