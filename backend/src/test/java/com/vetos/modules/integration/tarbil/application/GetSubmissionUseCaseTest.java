package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionNotFoundException;
import com.vetos.modules.patient.domain.PatientLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSubmissionUseCaseTest {

    @Mock private TarbilSyncLogRepository syncLogRepository;
    @Mock private TarbilValueMappingRepository mappingRepository;
    @Mock private VaccinationLookupPort vaccinationLookupPort;
    @Mock private PatientLookupPort patientLookupPort;

    private GetSubmissionUseCase useCase() {
        return new GetSubmissionUseCase(syncLogRepository,
            new TarbilSubmissionAssembler(vaccinationLookupPort, patientLookupPort, mappingRepository));
    }

    @Test
    void should_throwNotFound_when_submissionInAnotherTenant() {
        UUID id = UUID.randomUUID();
        when(syncLogRepository.findById(id)).thenReturn(Optional.of(
            TarbilSyncLog.queueVaccination(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())));

        assertThatThrownBy(() -> useCase().byId(UUID.randomUUID(), id)).isInstanceOf(TarbilSubmissionNotFoundException.class);
    }

    @Test
    void should_throwNotFound_when_vaccinationCancelled() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID vaccinationId = UUID.randomUUID();
        when(syncLogRepository.findById(id)).thenReturn(Optional.of(
            TarbilSyncLog.queueVaccination(tenantId, UUID.randomUUID(), vaccinationId)));
        when(vaccinationLookupPort.findForTarbil(vaccinationId)).thenReturn(Optional.of(new VaccinationTarbilView(
            vaccinationId, tenantId, UUID.randomUUID(), "Kuduz", null, LocalDate.now(), VaccinationStatus.CANCELLED)));

        assertThatThrownBy(() -> useCase().byId(tenantId, id)).isInstanceOf(TarbilSubmissionNotFoundException.class);
    }

    @Test
    void should_throwNotFound_when_noRowForVaccination() {
        UUID vaccinationId = UUID.randomUUID();
        when(syncLogRepository.findByVaccinationRecordId(vaccinationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().byVaccination(UUID.randomUUID(), vaccinationId))
            .isInstanceOf(TarbilSubmissionNotFoundException.class);
    }
}
