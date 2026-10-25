package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionNotFoundException;
import com.vetos.modules.inventory.domain.DeductionSettlementResult;
import com.vetos.modules.inventory.domain.ReportedDeductionLine;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.TarbilStockDeductionPort;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditLabels;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotent: eklentinin cevrimdisi kuyrugu ayni onayi birden fazla gonderebilir.
 * Asi bu arada iptal edildiyse de SUBMITTED kalici olur (TARBIL kaydi gercek); o durumda gorunum bos doner.
 */
@Service
@RequiredArgsConstructor
public class MarkSubmittedUseCase {

    private final TarbilSubmissionRepository syncLogRepository;
    private final TarbilSubmissionAssembler assembler;
    private final TarbilStockDeductionPort stockDeductionPort;
    private final AuditPort auditPort;

    @Transactional
    public Optional<TarbilSubmissionView> execute(UUID tenantId, UUID staffId, UUID submissionId,
                                        TarbilConfirmationMethod method, String tarbilReference,
                                        List<ReportedDeductionLine> lines) {
        TarbilSubmission log = syncLogRepository.findById(submissionId)
            .filter(l -> l.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilSubmissionNotFoundException(submissionId));
        String reference = tarbilReference == null || tarbilReference.isBlank() ? null : tarbilReference.trim();
        if (log.markSubmitted(staffId, method, reference, Instant.now())) {
            settleStock(log, method, lines, staffId);
            syncLogRepository.save(log);
            // yalnizca gercekten isaretlendiginde (idempotent tekrar gonderimler kayit uretmez)
            auditPort.record(AuditRecord.of(AuditAction.TARBIL_MARKED_SUBMITTED, "TARBIL_SUBMISSION", submissionId,
                AuditLabels.shortId(submissionId), null).forTenant(tenantId));
        }
        return assembler.assemble(log);
    }

    /**
     * Ilk onayda bekleyen stok duser (spec 2026-10-09 S5.3): MANUAL ya da satir yoksa planlanan adetler, aksi halde seri no
     * eslesen satirlar bildirilen adetle. Eslesmeyen bildirilen satirlar aktarima not edilir.
     */
    private void settleStock(TarbilSubmission log, TarbilConfirmationMethod method, List<ReportedDeductionLine> lines, UUID staffId) {
        StockReferenceType stockRef = log.sourceKindOrDefault().stockReference();
        if (stockRef == null) {
            return;
        }
        List<ReportedDeductionLine> reported = method == TarbilConfirmationMethod.MANUAL || lines == null || lines.isEmpty() ? null : lines;
        DeductionSettlementResult result = stockDeductionPort.settleFromTarbil(stockRef, log.getSourceId(), reported, staffId);
        if (!result.unmatched().isEmpty()) {
            log.noteSettlement("Vetly'de eslesmeyen TARBIL satirlari: " + result.unmatched().stream()
                .map(l -> l.productName() + " / " + l.serialNumber() + " x" + l.amount())
                .collect(Collectors.joining(", ")));
        }
    }
}

