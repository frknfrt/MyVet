package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.inventory.domain.InventoryItemLookupPort;
import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.patient.domain.PatientLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkSubmittedUseCaseTest {

    @Mock private TarbilSubmissionRepository syncLogRepository;

    @Mock private InventoryItemLookupPort inventoryItemLookupPort;
    @Mock private TarbilValueMappingRepository mappingRepository;
    @Mock private VaccinationLookupPort vaccinationLookupPort;
    @Mock private PatientLookupPort patientLookupPort;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    private MarkSubmittedUseCase useCase() {
        return new MarkSubmittedUseCase(syncLogRepository,
            new TarbilSubmissionAssembler(vaccinationLookupPort, patientLookupPort, mappingRepository, inventoryItemLookupPort));
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

    @Test
    void should_markAndSave_when_pending() {
        UUID id = UUID.randomUUID();
        TarbilSubmission log = pendingWithVaccination(id);

        useCase().execute(tenantId, staffId, id, TarbilConfirmationMethod.AUTO, "TRB-9");

        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.SUBMITTED);
        verify(syncLogRepository).save(log);
    }

    @Test
    void should_returnExistingWithoutChange_when_alreadySubmitted() {
        UUID id = UUID.randomUUID();
        TarbilSubmission log = pendingWithVaccination(id);
        log.markSubmitted(staffId, TarbilConfirmationMethod.AUTO, "TRB-1", Instant.now());

        useCase().execute(tenantId, staffId, id, TarbilConfirmationMethod.MANUAL, null);

        assertThat(log.getTarbilReference()).isEqualTo("TRB-1");
        verify(syncLogRepository, never()).save(any());
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

        assertThat(useCase().execute(tenantId, staffId, id, TarbilConfirmationMethod.MANUAL, null)).isEmpty();

        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.SUBMITTED);
        verify(syncLogRepository).save(log);
    }
}
