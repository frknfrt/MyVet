package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.PrescriptionDetail;
import com.vetos.modules.encounter.domain.DrugCatalog;
import com.vetos.modules.encounter.domain.DrugCatalogRepository;
import com.vetos.modules.encounter.domain.Prescription;
import com.vetos.modules.encounter.domain.PrescriptionItemRepository;
import com.vetos.modules.encounter.domain.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListPrescriptionsByPatientUseCase {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final DrugCatalogRepository drugCatalogRepository;

    @Transactional(readOnly = true)
    public List<PrescriptionDetail> execute(UUID patientId) {
        return prescriptionRepository.findByPatientId(patientId).stream()
            .map(this::toDetail)
            .toList();
    }

    private PrescriptionDetail toDetail(Prescription prescription) {
        var items = prescriptionItemRepository.findByPrescriptionId(prescription.getId()).stream()
            .map(item -> new PrescriptionDetail.PrescriptionItemDetail(
                item.getDrugId(),
                drugCatalogRepository.findById(item.getDrugId()).map(DrugCatalog::getName).orElse(null),
                item.getDosage(), item.getFrequency(), item.getDurationDays(), item.getRoute().name()
            ))
            .toList();

        return new PrescriptionDetail(
            prescription.getId(), prescription.getPatientId(), prescription.getEncounterId(),
            prescription.getIssuedDate(), prescription.getStatus(), prescription.isControlledSubstance(), items
        );
    }
}
