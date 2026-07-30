package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilStatusSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetTarbilStatusSummaryUseCase {

    private final TarbilSyncLogRepository tarbilSyncLogRepository;

    @Transactional(readOnly = true)
    public TarbilStatusSummary execute(UUID tenantId) {
        List<TarbilSyncLog> logs = tarbilSyncLogRepository.findByTenantId(tenantId);

        long pending = logs.stream().filter(l -> l.getStatus() == TarbilSyncStatus.PENDING).count();
        long synced = logs.stream().filter(l -> l.getStatus() == TarbilSyncStatus.SYNCED).count();
        long failed = logs.stream().filter(l -> l.getStatus() == TarbilSyncStatus.FAILED).count();
        Instant lastSyncedAt = logs.stream()
            .filter(l -> l.getStatus() == TarbilSyncStatus.SYNCED)
            .map(TarbilSyncLog::getAttemptedAt)
            .max(Instant::compareTo)
            .orElse(null);

        return new TarbilStatusSummary(pending, synced, failed, lastSyncedAt, true);
    }
}
