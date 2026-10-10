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

    @Transactional
    public UUID execute(IssuePrescriptionCommand command) {
        for (PrescriptionItemInput item : command.items()) {
            if ((item.inventoryItemId() == null) != (item.stockQuantity() == null)
                || (item.stockQuantity() != null && item.stockQuantity() <= 0)) {
                throw new InvalidPrescriptionItemException();
            }
        }
        List<PrescriptionIssuedEvent.StockItem> stockItems = command.items().stream()
            .filter(i -> i.inventoryItemId() != null)
            .map(i -> new PrescriptionIssuedEvent.StockItem(i.inventoryItemId(), i.stockQuantity()))
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
            prescription.getTenantId(), prescription.getId(), item.drugId(), item.dosage(),
            item.frequency(), item.durationDays(), item.route(), item.inventoryItemId(), item.stockQuantity()
        )));
        eventPublisher.publish(new PrescriptionIssuedEvent(prescription.getId(), command.patientId(), command.tarbilDiseaseId(), stockItems));
        return prescription.getId();
    }
}
