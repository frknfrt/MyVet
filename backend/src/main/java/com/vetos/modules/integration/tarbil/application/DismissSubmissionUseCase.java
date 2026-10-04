package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DismissSubmissionUseCase {

    private final TarbilSubmissionRepository syncLogRepository;

    @Transactional
    public void execute(UUID tenantId, UUID staffId, UUID submissionId, String reason) {
        TarbilSubmission log = syncLogRepository.findById(submissionId)
            .filter(l -> l.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilSubmissionNotFoundException(submissionId));
        String safeReason = reason == null || reason.isBlank() ? null : reason.trim();
        log.dismiss(staffId, safeReason, Instant.now());
        syncLogRepository.save(log);
    }
}
