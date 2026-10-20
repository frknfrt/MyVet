package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.integration.tarbil.domain.TarbilSourceKind;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.inventory.domain.DeductionSettlementResult;
import com.vetos.modules.inventory.domain.InventoryItemLookupPort;
import com.vetos.modules.inventory.domain.ReportedDeductionLine;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.TarbilStockDeductionPort;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkSubmittedUseCaseTest {

    @Mock private TarbilSubmissionRepository syncLogRepository;

    @Mock private InventoryItemLookupPort inventoryItemLookupPort;

    @Mock private com.vetos.modules.inventory.domain.TarbilStockDeductionPort assemblerDeductions;

    @Mock private com.vetos.modules.integration.tarbil.domain.TarbilDiseaseRepository diseaseRepository;
    @Mock private TarbilValueMappingRepository mappingRepository;
    @Mock private VaccinationLookupPort vaccinationLookupPort;
    @Mock private PatientLookupPort patientLookupPort;
    @Mock private TarbilStockDeductionPort deductions;
    @Mock private AuditPort auditPort;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    private MarkSubmittedUseCase useCase() {
        lenient().when(deductions.settleFromTarbil(any(), any(), any(), any())).thenReturn(new DeductionSettlementResult(0, List.of()));
        return new MarkSubmittedUseCase(syncLogRepository,
            new TarbilSubmissionAssembler(vaccinationLookupPort, patientLookupPort, mappingRepository, inventoryItemLookupPort,
                assemblerDeductions, diseaseRepository),
            deductions, auditPort);
    }

    private TarbilSubmission pendingWithVaccination(UUID id) {
        UUID vaccinationId = UUID.randomUUID();
        TarbilSubmission log = TarbilSubmission.queueVaccination(tenantId, UUID.randomUUID(), vaccinationId);
        when(syncLogRepository.findById(id)).thenReturn(Optional.of(log));
        lenient().when(vaccinationLookupPort.findForTarbil(vaccinationId)).thenReturn(Optional.of(new VaccinationTarbilView(
            vaccinationId, tenantId, log.getPatientId(), "Kuduz", null, LocalDate.now(), VaccinationStatus.ADMINISTERED)));
        lenient().when(patientLookupPort.findTarbilProfile(any())).thenReturn(Optional.empty());
        return log;
    }

    private TarbilSubmission encounterSubmission() {
        TarbilSubmission s = TarbilSubmission.queuePrescription(tenantId, UUID.randomUUID(), TarbilSourceKind.ENCOUNTER, UUID.randomUUID(), UUID.randomUUID());
        when(syncLogRepository.findById(s.getId())).thenReturn(Optional.of(s));
        lenient().when(patientLookupPort.findTarbilProfile(any())).thenReturn(Optional.empty());
        return s;
    }

    @Test
    void should_markAndSave_when_pending() {
        UUID id = UUID.randomUUID();
        TarbilSubmission log = pendingWithVaccination(id);

        useCase().execute(tenantId, staffId, id, TarbilConfirmationMethod.AUTO, "TRB-9", null);

        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.SUBMITTED);
        verify(syncLogRepository).save(log);
        verify(deductions).settleFromTarbil(StockReferenceType.VACCINATION, log.getSourceId(), null, staffId);
    }

    @Test
    void should_recordAuditEvent_when_marked() {
        UUID id = UUID.randomUUID();
        TarbilSubmission log = pendingWithVaccination(id);

        useCase().execute(tenantId, staffId, id, TarbilConfirmationMethod.AUTO, "TRB-9", null);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.tenantId()).isEqualTo(tenantId);
        assertThat(rec.action()).isEqualTo(AuditAction.TARBIL_MARKED_SUBMITTED);
        assertThat(rec.targetType()).isEqualTo("TARBIL_SUBMISSION");
        assertThat(rec.targetId()).isEqualTo(id);
        assertThat(rec.targetLabel()).isEqualTo(id.toString().substring(0, 8));
        assertThat(rec.summary()).isNull();
    }

    @Test
    void should_notRecord_whenNothingMarked() {
        UUID id = UUID.randomUUID();
        TarbilSubmission log = pendingWithVaccination(id);
        log.markSubmitted(staffId, TarbilConfirmationMethod.AUTO, "TRB-1", Instant.now());

        useCase().execute(tenantId, staffId, id, TarbilConfirmationMethod.MANUAL, null, null);

        verifyNoInteractions(auditPort);
    }

    @Test
    void should_returnExistingWithoutChange_when_alreadySubmitted() {
        UUID id = UUID.randomUUID();
        TarbilSubmission log = pendingWithVaccination(id);
        log.markSubmitted(staffId, TarbilConfirmationMethod.AUTO, "TRB-1", Instant.now());

        useCase().execute(tenantId, staffId, id, TarbilConfirmationMethod.MANUAL, null, null);

        assertThat(log.getTarbilReference()).isEqualTo("TRB-1");
        verify(syncLogRepository, never()).save(any());
        verify(deductions, never()).settleFromTarbil(any(), any(), any(), any());
    }

    @Test
    void should_persistSubmitted_when_vaccinationCancelledMeanwhile() {
        // Cevrimdisi kuyruktan gelen onay: asi bu arada Vetly'de iptal edilmis. TARBIL kaydi gercek -- kaybolmamali.
        UUID id = UUID.randomUUID();
        UUID vaccinationId = UUID.randomUUID();
        TarbilSubmission log = TarbilSubmission.queueVaccination(tenantId, UUID.randomUUID(), vaccinationId);
        when(syncLogRepository.findById(id)).thenReturn(Optional.of(log));
        when(vaccinationLookupPort.findForTarbil(vaccinationId)).thenReturn(Optional.of(new VaccinationTarbilView(
            vaccinationId, tenantId, log.getPatientId(), "Kuduz", null, LocalDate.now(), VaccinationStatus.CANCELLED)));

        assertThat(useCase().execute(tenantId, staffId, id, TarbilConfirmationMethod.MANUAL, null, null)).isEmpty();

        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.SUBMITTED);
        verify(syncLogRepository).save(log);
    }

    @Test
    void settlesReportedLinesOnFirstConfirmation() {
        TarbilSubmission s = encounterSubmission();
        List<ReportedDeductionLine> lines = List.of(new ReportedDeductionLine("Synulox", "S1", 2));

        useCase().execute(tenantId, staffId, s.getId(), TarbilConfirmationMethod.AUTO, null, lines);

        verify(deductions).settleFromTarbil(StockReferenceType.ENCOUNTER, s.getSourceId(), lines, staffId);
        assertThat(s.getSettlementNote()).isNull();
    }

    @Test
    void manualConfirmationSettlesPlannedQuantities() {
        TarbilSubmission s = encounterSubmission();

        useCase().execute(tenantId, staffId, s.getId(), TarbilConfirmationMethod.MANUAL, null, List.of(new ReportedDeductionLine("X", "Y", 1)));

        verify(deductions).settleFromTarbil(StockReferenceType.ENCOUNTER, s.getSourceId(), null, staffId);
    }

    @Test
    void secondConfirmationDoesNotDeductAgain() {
        TarbilSubmission s = encounterSubmission();
        MarkSubmittedUseCase useCase = useCase();
        useCase.execute(tenantId, staffId, s.getId(), TarbilConfirmationMethod.AUTO, null, null);

        useCase.execute(tenantId, staffId, s.getId(), TarbilConfirmationMethod.MANUAL, null, null);

        verify(deductions, times(1)).settleFromTarbil(any(), any(), isNull(), any());
    }

    @Test
    void notesReportedLinesThatMatchedNothing() {
        TarbilSubmission s = encounterSubmission();
        MarkSubmittedUseCase useCase = useCase();
        when(deductions.settleFromTarbil(any(), any(), any(), any()))
            .thenReturn(new DeductionSettlementResult(0, List.of(new ReportedDeductionLine("Baytril", "B9", 1))));

        useCase.execute(tenantId, staffId, s.getId(), TarbilConfirmationMethod.AUTO, null, List.of(new ReportedDeductionLine("Baytril", "B9", 1)));

        assertThat(s.getSettlementNote()).contains("Baytril").contains("B9");
    }
}
