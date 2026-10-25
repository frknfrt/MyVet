package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import com.vetos.modules.integration.tarbil.domain.TarbilSourceKind;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.modules.inventory.domain.PendingDeductionLine;
import com.vetos.modules.inventory.domain.PendingDeductionStatus;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.TarbilStockDeductionPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingTarbilStockWorkTest {

    @Mock private TarbilSubmissionRepository submissions;
    @Mock private TarbilStockDeductionPort deductions;
    @Mock private TarbilSubmissionAssembler assembler;

    private final UUID tenantId = UUID.randomUUID();

    private PendingTarbilStockWork work() {
        return new PendingTarbilStockWork(submissions, deductions, assembler);
    }

    private PendingDeductionLine deduction(UUID itemId, PendingDeductionStatus status) {
        return new PendingDeductionLine(UUID.randomUUID(), itemId, "Kalem", null, "L1", java.math.BigDecimal.ONE, status, null, "ADET", java.math.BigDecimal.ZERO);
    }

    @Test
    void should_countVaccinationAlreadyDeductedInVetly_by_itsStockItem() {
        UUID item = UUID.randomUUID();
        TarbilSubmission vaccination = TarbilSubmission.queueVaccination(tenantId, UUID.randomUUID(), UUID.randomUUID());
        when(submissions.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING)).thenReturn(List.of(vaccination));
        when(deductions.listForReference(StockReferenceType.VACCINATION, vaccination.getSourceId())).thenReturn(List.of());
        when(assembler.liveVaccination(vaccination)).thenReturn(Optional.of(new VaccinationTarbilView(
            vaccination.getSourceId(), tenantId, UUID.randomUUID(), "Biocan DHPPI", "145932", LocalDate.now(),
            VaccinationStatus.ADMINISTERED, item)));

        assertThat(work().countByItem(tenantId)).containsExactlyEntriesOf(java.util.Map.of(item, 1));
    }

    @Test
    void should_countEachPendingSubmissionOnce_evenWhenItHasBothSources() {
        UUID item = UUID.randomUUID();
        TarbilSubmission vaccination = TarbilSubmission.queueVaccination(tenantId, UUID.randomUUID(), UUID.randomUUID());
        TarbilSubmission prescription = TarbilSubmission.queuePrescription(tenantId, UUID.randomUUID(), TarbilSourceKind.PRESCRIPTION,
            UUID.randomUUID(), UUID.randomUUID());
        when(submissions.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING)).thenReturn(List.of(vaccination, prescription));
        when(deductions.listForReference(StockReferenceType.VACCINATION, vaccination.getSourceId()))
            .thenReturn(List.of(deduction(item, PendingDeductionStatus.PENDING)));
        when(assembler.liveVaccination(vaccination)).thenReturn(Optional.of(new VaccinationTarbilView(
            vaccination.getSourceId(), tenantId, UUID.randomUUID(), "Biocan R", "665932", LocalDate.now(),
            VaccinationStatus.ADMINISTERED, item)));
        when(deductions.listForReference(StockReferenceType.PRESCRIPTION, prescription.getSourceId()))
            .thenReturn(List.of(deduction(item, PendingDeductionStatus.PENDING)));
        lenient().when(assembler.liveVaccination(prescription)).thenReturn(Optional.empty());

        assertThat(work().countByItem(tenantId)).containsExactlyEntriesOf(java.util.Map.of(item, 2));
    }

    @Test
    void should_ignoreDeductionLinesThatAreNoLongerPending() {
        TarbilSubmission prescription = TarbilSubmission.queuePrescription(tenantId, UUID.randomUUID(), TarbilSourceKind.ENCOUNTER,
            UUID.randomUUID(), UUID.randomUUID());
        when(submissions.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING)).thenReturn(List.of(prescription));
        when(deductions.listForReference(any(), any())).thenReturn(List.of(
            deduction(UUID.randomUUID(), PendingDeductionStatus.DEDUCTED),
            deduction(UUID.randomUUID(), PendingDeductionStatus.CANCELLED)));
        when(assembler.liveVaccination(prescription)).thenReturn(Optional.empty());

        assertThat(work().countByItem(tenantId)).isEmpty();
    }
}
