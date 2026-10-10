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

import java.util.Optional;
import java.util.UUID;

/**
 * Muayene karti "TARBIL'den stok dus" (spec 2026-10-09 S5.1): muayenede kullanilan TARBIL'e bagli ilaclar icin stoktan
 * dusen recete aktarimi kuyruga girer. Idempotent: bekleyen aktarim varsa id'si doner; reddedilmis (Bildirilmeyecek)
 * aktarim yeni teshisle geri acilir; gonderilmis aktarimda kalan kalemler icin 409 (ikinci aktarim yok, Plan B).
 * Kiraci disi muayene EncounterLookupPort'ta 404 olur (filtreli oturum).
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
        Optional<TarbilSubmission> existing = submissions.findByDocumentTypeAndSourceId(TarbilDocumentType.PRESCRIPTION, encounterId)
            .filter(s -> s.getTenantId().equals(TenantContext.current()));
        if (existing.isEmpty()) {
            return submissions.save(TarbilSubmission.queuePrescription(
                TenantContext.current(), encounter.patientId(), TarbilSourceKind.ENCOUNTER, encounterId, tarbilDiseaseId)).getId();
        }
        TarbilSubmission submission = existing.get();
        switch (submission.getStatus()) {
            case DISMISSED -> {
                submission.restore();
                submission.changeTarbilDisease(tarbilDiseaseId);
                submissions.save(submission);
            }
            case SUBMITTED -> throw new TarbilNothingToDeductConflictException(
                "Bu muayenenin reçetesi TARBİL'e gönderildi; kalan kalemler için Yalnız Vetly'den düş'ü kullanın");
            case PENDING -> { }
        }
        return submission.getId();
    }
}
