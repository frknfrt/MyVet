package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.encounter.domain.EncounterLookupPort;
import com.vetos.modules.encounter.domain.EncounterStatus;
import com.vetos.modules.encounter.domain.EncounterSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.domain.TarbilSourceKind;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilNothingToDeductConflictException;
import com.vetos.modules.inventory.domain.PendingDeductionStatus;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.TarbilStockDeductionPort;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Muayene karti "TARBIL'den stok dus" (spec 2026-10-09 S5.1): muayenede kullanilan TARBIL'e bagli ilaclar icin stoktan
 * dusen recete aktarimi kuyruga girer. Idempotent: ayni muayene icin mevcut aktarimin id'si doner. Kiraci disi muayene
 * EncounterLookupPort'ta 404 olur (filtreli oturum).
 */
@Service
@RequiredArgsConstructor
public class QueueEncounterDeductionUseCase {

    private final EncounterLookupPort encounterLookupPort;
    private final TarbilStockDeductionPort stockDeductionPort;
    private final TarbilSubmissionRepository submissions;

    @Transactional
    public UUID execute(UUID encounterId, UUID tarbilDiseaseId) {
        EncounterSummary encounter = encounterLookupPort.findSummaryById(encounterId);
        if (encounter.status() == EncounterStatus.DRAFT) {
            throw new TarbilNothingToDeductConflictException("Muayene tamamlanmadı");
        }
        boolean anyPending = stockDeductionPort.listForReference(StockReferenceType.ENCOUNTER, encounterId).stream()
            .anyMatch(l -> l.status() == PendingDeductionStatus.PENDING);
        if (!anyPending) {
            throw new TarbilNothingToDeductConflictException("TARBİL düşümü bekleyen ilaç yok");
        }
        return submissions.findByDocumentTypeAndSourceId(TarbilDocumentType.PRESCRIPTION, encounterId)
            .filter(s -> s.getTenantId().equals(TenantContext.current()))
            .map(TarbilSubmission::getId)
            .orElseGet(() -> submissions.save(TarbilSubmission.queuePrescription(
                TenantContext.current(), encounter.patientId(), TarbilSourceKind.ENCOUNTER, encounterId, tarbilDiseaseId)).getId());
    }
}
