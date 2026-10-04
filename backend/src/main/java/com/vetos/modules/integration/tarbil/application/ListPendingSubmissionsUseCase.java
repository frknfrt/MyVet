package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
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

    private final TarbilSubmissionRepository syncLogRepository;
    private final TarbilSubmissionAssembler assembler;

    @Transactional(readOnly = true)
    public List<TarbilSubmissionView> execute(UUID tenantId) {
        return execute(tenantId, null);
    }

    /** typeOrNull null ise tum turler (eklentinin parametresiz cagrisi eskisiyle ayni sonucu verir). */
    @Transactional(readOnly = true)
    public List<TarbilSubmissionView> execute(UUID tenantId, TarbilDocumentType typeOrNull) {
        return syncLogRepository.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING).stream()
            .filter(s -> typeOrNull == null || s.getDocumentType() == typeOrNull)
            .map(assembler::assemble)
            .flatMap(Optional::stream)
            .toList();
    }
}
