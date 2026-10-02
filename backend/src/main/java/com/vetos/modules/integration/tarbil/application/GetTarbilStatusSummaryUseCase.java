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
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetTarbilStatusSummaryUseCase {

    private final TarbilSyncLogRepository tarbilSyncLogRepository;

    @Transactional(readOnly = true)
    public TarbilStatusSummary execute(UUID tenantId) {
        List<TarbilSyncLog> logs = tarbilSyncLogRepository.findByTenantId(tenantId);
        long pending = logs.stream().filter(l -> l.getStatus() == TarbilSyncStatus.PENDING).count();
        long submitted = logs.stream().filter(l -> l.getStatus() == TarbilSyncStatus.SUBMITTED).count();
        long dismissed = logs.stream().filter(l -> l.getStatus() == TarbilSyncStatus.DISMISSED).count();
        Instant lastSubmittedAt = logs.stream()
            .map(TarbilSyncLog::getSubmittedAt)
            .filter(Objects::nonNull)
            .max(Instant::compareTo)
            .orElse(null);
        return new TarbilStatusSummary(pending, submitted, dismissed, lastSubmittedAt);
    }
}
