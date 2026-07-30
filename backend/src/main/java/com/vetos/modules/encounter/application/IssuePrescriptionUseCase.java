package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.IssuePrescriptionCommand;
import com.vetos.modules.encounter.domain.Prescription;
import com.vetos.modules.encounter.domain.PrescriptionItem;
import com.vetos.modules.encounter.domain.PrescriptionItemRepository;
import com.vetos.modules.encounter.domain.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssuePrescriptionUseCase {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;

    @Transactional
    public UUID execute(IssuePrescriptionCommand command) {
        Prescription prescription = prescriptionRepository.save(Prescription.issue(
            command.patientId(), command.encounterId(), command.prescribingStaffId(), command.controlledSubstance()
        ));

        command.items().forEach(item -> prescriptionItemRepository.save(PrescriptionItem.add(
            prescription.getId(), item.drugId(), item.dosage(), item.frequency(), item.durationDays(), item.route()
        )));

        return prescription.getId();
    }
}
