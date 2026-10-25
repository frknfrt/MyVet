package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.IssuePrescriptionCommand;
import com.vetos.modules.encounter.application.dto.PrescriptionItemInput;
import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.Prescription;
import com.vetos.modules.encounter.domain.PrescriptionItem;
import com.vetos.modules.encounter.domain.PrescriptionItemRepository;
import com.vetos.modules.encounter.domain.PrescriptionRepository;
import com.vetos.modules.encounter.domain.event.PrescriptionIssuedEvent;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import com.vetos.modules.encounter.domain.exception.InvalidPrescriptionItemException;
import com.vetos.modules.encounter.domain.exception.PrescriptionEncounterMismatchException;
import com.vetos.modules.encounter.domain.exception.PrescriptionTarbilDiseaseRequiredException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditLabels;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import com.vetos.platform.event.DomainEventPublisher;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Muayeneli ya da muayenesiz recete (spec 2026-10-09 S5.6); stoktan verilen kalemler TARBIL dusumunu bekler (olayla). */
@Service
@RequiredArgsConstructor
public class IssuePrescriptionUseCase {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final EncounterRepository encounterRepository;
    private final DomainEventPublisher eventPublisher;
    private final AuditPort auditPort;

    @Transactional
    public UUID execute(IssuePrescriptionCommand command) {
        for (PrescriptionItemInput item : command.items()) {
            if (!validItem(item)) {
                throw new InvalidPrescriptionItemException();
            }
        }
        List<PrescriptionIssuedEvent.StockItem> stockItems = command.items().stream()
            .filter(i -> i.inventoryItemId() != null)
            .map(i -> new PrescriptionIssuedEvent.StockItem(i.inventoryItemId(), i.stockQuantity(), i.drugName().trim()))
            .toList();
        if (!stockItems.isEmpty() && command.tarbilDiseaseId() == null) {
            throw new PrescriptionTarbilDiseaseRequiredException();
        }
        if (command.encounterId() != null) {
            Encounter encounter = encounterRepository.findById(command.encounterId())
                .orElseThrow(() -> new EncounterNotFoundException(command.encounterId()));
            if (!encounter.getPatientId().equals(command.patientId())) {
                throw new PrescriptionEncounterMismatchException();
            }
        }
        Prescription prescription = prescriptionRepository.save(Prescription.issue(
            TenantContext.current(), command.patientId(), command.encounterId(),
            command.prescribingStaffId(), command.controlledSubstance(), command.tarbilDiseaseId()
        ));
        command.items().forEach(item -> prescriptionItemRepository.save(PrescriptionItem.add(
            prescription.getTenantId(), prescription.getId(), item.drugId(), item.drugName() == null ? null : item.drugName().trim(),
            item.dosage(), item.frequency(), item.durationDays(), item.route(), item.inventoryItemId(), item.stockQuantity()
        )));
        eventPublisher.publish(new PrescriptionIssuedEvent(prescription.getId(), command.patientId(), command.tarbilDiseaseId(), stockItems));
        auditPort.record(AuditRecord.of(AuditAction.PRESCRIPTION_ISSUED, "PRESCRIPTION", prescription.getId(),
            AuditLabels.shortId(prescription.getId()), command.items().size() + " kalem"));
        return prescription.getId();
    }

    /**
     * Stok kalemi ve adedi birlikte; stoktan verilen kalemde ilac stok urunudur (ad dolu, katalog ilaci yok), digerlerinde
     * katalog ilaci zorunlu ve ad yok (spec 2026-10-12).
     */
    private static boolean validItem(PrescriptionItemInput item) {
        if ((item.inventoryItemId() == null) != (item.stockQuantity() == null)) return false;
        if (item.stockQuantity() != null && item.stockQuantity().signum() <= 0) return false;
        boolean named = item.drugName() != null && !item.drugName().isBlank();
        return item.inventoryItemId() != null
            ? item.drugId() == null && named
            : item.drugId() != null && item.drugName() == null;
    }
}
