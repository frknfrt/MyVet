package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.TarbilDisease;
import com.vetos.modules.integration.tarbil.domain.TarbilDiseaseRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.domain.TarbilSourceKind;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.inventory.domain.InventoryItemLookupPort;
import com.vetos.modules.inventory.domain.PendingDeductionLine;
import com.vetos.modules.inventory.domain.PendingDeductionStatus;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.TarbilStockDeductionPort;
import com.vetos.modules.patient.domain.PatientLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Eklentiye giden stoktan dusen recete aktarimi (spec 2026-10-09 S7). */
@ExtendWith(MockitoExtension.class)
class TarbilSubmissionAssemblerTest {

    @Mock private VaccinationLookupPort vaccinations;
    @Mock private PatientLookupPort patients;
    @Mock private TarbilValueMappingRepository mappings;
    @Mock private InventoryItemLookupPort inventoryItems;
    @Mock private TarbilStockDeductionPort deductions;
    @Mock private TarbilDiseaseRepository diseases;

    private TarbilSubmissionAssembler assembler() {
        return new TarbilSubmissionAssembler(vaccinations, patients, mappings, inventoryItems, deductions, diseases);
    }

    private PendingDeductionLine line(String name, String product, String lot, int qty, PendingDeductionStatus status) {
        return new PendingDeductionLine(UUID.randomUUID(), UUID.randomUUID(), name, product, lot, qty, status, null);
    }

    @Test
    void assemblesAPrescriptionWithItsPendingLines() {
        UUID disease = UUID.randomUUID();
        TarbilSubmission s = TarbilSubmission.queuePrescription(UUID.randomUUID(), UUID.randomUUID(), TarbilSourceKind.ENCOUNTER, UUID.randomUUID(), disease);
        when(deductions.listForReference(StockReferenceType.ENCOUNTER, s.getSourceId())).thenReturn(List.of(
            line("Synulox", "SYNULOX 250 MG", "S1", 2, PendingDeductionStatus.PENDING),
            line("Metacam", "METACAM", "M7", 1, PendingDeductionStatus.DEDUCTED)));
        when(diseases.findById(disease)).thenReturn(Optional.of(TarbilDisease.of(disease, null, "Pnömoni", true, 1)));
        lenient().when(patients.findTarbilProfile(s.getPatientId())).thenReturn(Optional.empty());

        TarbilSubmissionView v = assembler().assemble(s).orElseThrow();

        assertThat(v.documentType()).isEqualTo(TarbilDocumentType.PRESCRIPTION);
        assertThat(v.prescription().sourceKind()).isEqualTo("ENCOUNTER");
        assertThat(v.prescription().tarbilDiseaseName()).isEqualTo("Pnömoni");
        assertThat(v.prescription().lines()).containsExactly(new TarbilSubmissionView.PrescriptionPayload.Line("Synulox", "SYNULOX 250 MG", "S1", 2));
        assertThat(v.vaccineName()).isEqualTo("Reçete");
        assertThat(assembler().isVisible(s)).isTrue();
    }

    @Test
    void pendingPrescriptionWithNothingPendingIsHidden() {
        TarbilSubmission s = TarbilSubmission.queuePrescription(UUID.randomUUID(), UUID.randomUUID(), TarbilSourceKind.PRESCRIPTION, UUID.randomUUID(), null);
        when(deductions.listForReference(StockReferenceType.PRESCRIPTION, s.getSourceId())).thenReturn(List.of());

        assertThat(assembler().isVisible(s)).isFalse();
        assertThat(assembler().assemble(s)).isEmpty();
    }
}
