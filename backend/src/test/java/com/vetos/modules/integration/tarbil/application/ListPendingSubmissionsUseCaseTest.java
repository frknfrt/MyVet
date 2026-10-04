package com.vetos.modules.integration.tarbil.application;

import static org.mockito.Mockito.verifyNoInteractions;
import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMapping;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.PatientTarbilProfile;
import com.vetos.modules.patient.domain.Sex;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListPendingSubmissionsUseCaseTest {

    @Mock private TarbilSubmissionRepository syncLogRepository;
    @Mock private TarbilValueMappingRepository mappingRepository;
    @Mock private VaccinationLookupPort vaccinationLookupPort;
    @Mock private PatientLookupPort patientLookupPort;

    private final UUID tenantId = UUID.randomUUID();

    private ListPendingSubmissionsUseCase useCase() {
        return new ListPendingSubmissionsUseCase(syncLogRepository,
            new TarbilSubmissionAssembler(vaccinationLookupPort, patientLookupPort, mappingRepository));
    }

    @Test
    void should_includeMappingsAndPatientData_when_pendingVaccinationExists() {
        UUID patientId = UUID.randomUUID();
        UUID vaccinationId = UUID.randomUUID();
        UUID speciesId = UUID.randomUUID();
        TarbilSubmission log = TarbilSubmission.queueVaccination(tenantId, patientId, vaccinationId);
        when(syncLogRepository.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING)).thenReturn(List.of(log));
        when(vaccinationLookupPort.findForTarbil(vaccinationId)).thenReturn(Optional.of(new VaccinationTarbilView(
            vaccinationId, tenantId, patientId, "Kuduz Aşısı", "L-1", LocalDate.of(2026, 10, 1), VaccinationStatus.ADMINISTERED)));
        when(patientLookupPort.findTarbilProfile(patientId)).thenReturn(Optional.of(new PatientTarbilProfile(
            patientId, "Pamuk", null, "TR-34-000123", speciesId, "Kedi", null, Sex.FEMALE, null)));
        TarbilValueMapping vaccineMapping = TarbilValueMapping.create(
            tenantId, TarbilMappingKind.VACCINE, "kuduz aşisi", "{\"vaccine\":{}}", UUID.randomUUID(), Instant.now());
        when(mappingRepository.findByTenantIdAndKindAndVetlyKey(tenantId, TarbilMappingKind.VACCINE, "kuduz aşisi"))
            .thenReturn(Optional.of(vaccineMapping));
        when(mappingRepository.findByTenantIdAndKindAndVetlyKey(eq(tenantId), eq(TarbilMappingKind.SPECIES), any()))
            .thenReturn(Optional.empty());

        List<TarbilSubmissionView> result = useCase().execute(tenantId);

        assertThat(result).hasSize(1);
        TarbilSubmissionView view = result.get(0);
        assertThat(view.patientName()).isEqualTo("Pamuk");
        assertThat(view.microchipNumber()).isNull();
        assertThat(view.passportNumber()).isEqualTo("TR-34-000123");
        assertThat(view.sex()).isEqualTo("FEMALE");
        assertThat(view.vaccineKey()).isEqualTo("kuduz aşisi");
        assertThat(view.vaccineMappingJson()).isEqualTo("{\"vaccine\":{}}");
        assertThat(view.speciesMappingJson()).isNull();
    }

    @Test
    void should_skipCancelledAndMissingVaccinations_when_listingPending() {
        UUID cancelledId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        when(syncLogRepository.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING)).thenReturn(List.of(
            TarbilSubmission.queueVaccination(tenantId, patientId, cancelledId),
            TarbilSubmission.queueVaccination(tenantId, patientId, missingId)));
        when(vaccinationLookupPort.findForTarbil(cancelledId)).thenReturn(Optional.of(new VaccinationTarbilView(
            cancelledId, tenantId, patientId, "Karma", null, LocalDate.now(), VaccinationStatus.CANCELLED)));
        when(vaccinationLookupPort.findForTarbil(missingId)).thenReturn(Optional.empty());

        assertThat(useCase().execute(tenantId)).isEmpty();
    }

    @Test
    void should_notAssemble_when_documentTypeIsNotVaccination() {
        TarbilSubmission prescription = TarbilSubmission.queue(tenantId, TarbilDocumentType.PRESCRIPTION, UUID.randomUUID(), UUID.randomUUID());
        when(syncLogRepository.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING)).thenReturn(List.of(prescription));

        assertThat(useCase().execute(tenantId)).isEmpty();
        verifyNoInteractions(vaccinationLookupPort);
    }

    @Test
    void should_returnAllTypes_when_noTypeGiven() {
        UUID patientId = UUID.randomUUID();
        UUID vaccinationId = UUID.randomUUID();
        TarbilSubmission vaccination = TarbilSubmission.queueVaccination(tenantId, patientId, vaccinationId);
        when(syncLogRepository.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING)).thenReturn(List.of(vaccination));
        when(vaccinationLookupPort.findForTarbil(vaccinationId)).thenReturn(Optional.of(new VaccinationTarbilView(
            vaccinationId, tenantId, patientId, "Karma", null, LocalDate.of(2026, 10, 1), VaccinationStatus.ADMINISTERED)));
        when(patientLookupPort.findTarbilProfile(patientId)).thenReturn(Optional.empty());

        assertThat(useCase().execute(tenantId)).hasSize(1);
        assertThat(useCase().execute(tenantId, null)).hasSize(1);
        assertThat(useCase().execute(tenantId, TarbilDocumentType.VACCINATION).get(0).documentType()).isEqualTo(TarbilDocumentType.VACCINATION);
        assertThat(useCase().execute(tenantId, TarbilDocumentType.PRESCRIPTION)).isEmpty();
    }
}
