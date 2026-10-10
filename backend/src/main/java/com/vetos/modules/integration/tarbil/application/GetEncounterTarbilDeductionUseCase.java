package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.encounter.domain.EncounterLookupPort;
import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.modules.inventory.domain.PendingDeductionLine;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.TarbilStockDeductionPort;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Muayene sayfasindaki TARBIL dusum karti (spec 2026-10-09 S5.5). */
@Service
@RequiredArgsConstructor
public class GetEncounterTarbilDeductionUseCase {

    private final EncounterLookupPort encounterLookupPort;
    private final TarbilStockDeductionPort stockDeductionPort;
    private final TarbilSubmissionRepository submissions;

    public record View(List<PendingDeductionLine> lines, UUID submissionId, TarbilSyncStatus submissionStatus) {}

    @Transactional(readOnly = true)
    public View execute(UUID encounterId) {
        encounterLookupPort.findSummaryById(encounterId); // kiraci disi -> 404
        Optional<TarbilSubmission> submission = submissions.findByDocumentTypeAndSourceId(TarbilDocumentType.PRESCRIPTION, encounterId)
            .filter(s -> s.getTenantId().equals(TenantContext.current()));
        return new View(stockDeductionPort.listForReference(StockReferenceType.ENCOUNTER, encounterId),
            submission.map(TarbilSubmission::getId).orElse(null), submission.map(TarbilSubmission::getStatus).orElse(null));
    }
}
