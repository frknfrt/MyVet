package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Idempotent: eklentinin cevrimdisi kuyrugu ayni onayi birden fazla gonderebilir. */
@Service
@RequiredArgsConstructor
public class MarkSubmittedUseCase {

    private final TarbilSyncLogRepository syncLogRepository;
    private final TarbilSubmissionAssembler assembler;

    @Transactional
    public TarbilSubmissionView execute(UUID tenantId, UUID staffId, UUID submissionId,
                                        TarbilConfirmationMethod method, String tarbilReference) {
        TarbilSyncLog log = syncLogRepository.findById(submissionId)
            .filter(l -> l.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilSubmissionNotFoundException(submissionId));
        String reference = tarbilReference == null || tarbilReference.isBlank() ? null : tarbilReference.trim();
        if (log.markSubmitted(staffId, method, reference, Instant.now())) {
            syncLogRepository.save(log);
        }
        return assembler.assemble(log).orElseThrow(() -> new TarbilSubmissionNotFoundException(submissionId));
    }
}
