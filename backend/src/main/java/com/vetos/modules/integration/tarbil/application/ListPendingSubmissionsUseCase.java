package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListPendingSubmissionsUseCase {

    private final TarbilSyncLogRepository syncLogRepository;
    private final TarbilSubmissionAssembler assembler;

    @Transactional(readOnly = true)
    public List<TarbilSubmissionView> execute(UUID tenantId) {
        return syncLogRepository.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING).stream()
            .map(assembler::assemble)
            .flatMap(Optional::stream)
            .toList();
    }
}
