package com.vetos.modules.integration.tarbil.infrastructure.event;

import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.event.PendingDeductionSettledVetlyOnlyEvent;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * "Yalniz Vetly'den dus" sonrasi bekleyen stoktan dusen recete aktarimi reddedilir (spec 2026-10-09 S5.1). Asi aktarimina
 * dokunulmaz: asinin kendisi TARBIL'e bildirilmeli, Vetly'den dusmek yalniz stogu kapatir (review 2026-10-10).
 */
@Component
@RequiredArgsConstructor
class VetlyOnlyDeductionListener {

    static final String REASON = "Yalnız Vetly'den düşüldü";

    private final TarbilSubmissionRepository repository;

    @EventListener
    void on(PendingDeductionSettledVetlyOnlyEvent event) {
        if (event.referenceType() == StockReferenceType.VACCINATION) {
            return;
        }
        repository.findByDocumentTypeAndSourceId(TarbilDocumentType.PRESCRIPTION, event.referenceId())
            .filter(s -> s.getTenantId().equals(TenantContext.current()))
            .filter(s -> s.getStatus() == TarbilSyncStatus.PENDING)
            .ifPresent(s -> {
                s.dismiss(event.staffId(), REASON, Instant.now());
                repository.save(s);
            });
    }
}
