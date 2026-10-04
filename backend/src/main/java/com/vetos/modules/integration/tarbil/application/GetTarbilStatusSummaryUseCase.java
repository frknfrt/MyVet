package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.application.dto.TarbilStatusSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
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

    private final TarbilSubmissionRepository tarbilSyncLogRepository;
    private final TarbilSubmissionAssembler assembler;

    @Transactional(readOnly = true)
    public TarbilStatusSummary execute(UUID tenantId) {
        List<TarbilSubmission> logs = tarbilSyncLogRepository.findByTenantId(tenantId).stream()
            // Bu web listesi asi aktarimlarini gosterir; diger belge turleri kendi ekranlarina gelecek (P1+).
            .filter(log -> log.getDocumentType() == TarbilDocumentType.VACCINATION)
            .filter(assembler::isVisible)
            .toList();
        long pending = logs.stream().filter(l -> l.getStatus() == TarbilSyncStatus.PENDING).count();
        long submitted = logs.stream().filter(l -> l.getStatus() == TarbilSyncStatus.SUBMITTED).count();
        long dismissed = logs.stream().filter(l -> l.getStatus() == TarbilSyncStatus.DISMISSED).count();
        Instant lastSubmittedAt = logs.stream()
            .map(TarbilSubmission::getSubmittedAt)
            .filter(Objects::nonNull)
            .max(Instant::compareTo)
            .orElse(null);
        return new TarbilStatusSummary(pending, submitted, dismissed, lastSubmittedAt);
    }
}
