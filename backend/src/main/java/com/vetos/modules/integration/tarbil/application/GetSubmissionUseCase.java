package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetSubmissionUseCase {

    private final TarbilSyncLogRepository syncLogRepository;
    private final TarbilSubmissionAssembler assembler;

    @Transactional(readOnly = true)
    public TarbilSubmissionView byId(UUID tenantId, UUID submissionId) {
        return resolve(tenantId, syncLogRepository.findById(submissionId), submissionId);
    }

    @Transactional(readOnly = true)
    public TarbilSubmissionView byVaccination(UUID tenantId, UUID vaccinationRecordId) {
        return resolve(tenantId, syncLogRepository.findByVaccinationRecordId(vaccinationRecordId), vaccinationRecordId);
    }

    private TarbilSubmissionView resolve(UUID tenantId, Optional<TarbilSyncLog> log, UUID requestedId) {
        return log.filter(l -> l.getTenantId().equals(tenantId))
            .flatMap(assembler::assemble)
            .orElseThrow(() -> new TarbilSubmissionNotFoundException(requestedId));
    }
}
