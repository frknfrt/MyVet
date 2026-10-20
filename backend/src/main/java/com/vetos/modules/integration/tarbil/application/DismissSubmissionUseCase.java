package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditLabels;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DismissSubmissionUseCase {

    private static final int MAX_REASON_LENGTH = 200;

    private final TarbilSubmissionRepository syncLogRepository;
    private final AuditPort auditPort;

    @Transactional
    public void execute(UUID tenantId, UUID staffId, UUID submissionId, String reason) {
        TarbilSubmission log = syncLogRepository.findById(submissionId)
            .filter(l -> l.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilSubmissionNotFoundException(submissionId));
        String safeReason = reason == null || reason.isBlank() ? null : reason.trim();
        log.dismiss(staffId, safeReason, Instant.now());
        syncLogRepository.save(log);
        String summary = safeReason != null && safeReason.length() > MAX_REASON_LENGTH
            ? safeReason.substring(0, MAX_REASON_LENGTH) : safeReason;
        auditPort.record(AuditRecord.of(AuditAction.TARBIL_SUBMISSION_DISMISSED, "TARBIL_SUBMISSION", submissionId,
            AuditLabels.shortId(submissionId), summary).forTenant(tenantId));
    }
}
