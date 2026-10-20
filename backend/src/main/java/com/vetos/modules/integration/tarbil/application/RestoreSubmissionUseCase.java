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

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestoreSubmissionUseCase {

    private final TarbilSubmissionRepository syncLogRepository;
    private final AuditPort auditPort;

    @Transactional
    public void execute(UUID tenantId, UUID submissionId) {
        TarbilSubmission log = syncLogRepository.findById(submissionId)
            .filter(l -> l.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilSubmissionNotFoundException(submissionId));
        log.restore();
        syncLogRepository.save(log);
        auditPort.record(AuditRecord.of(AuditAction.TARBIL_SUBMISSION_RESTORED, "TARBIL_SUBMISSION", submissionId,
            AuditLabels.shortId(submissionId), null).forTenant(tenantId));
    }
}
