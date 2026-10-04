package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.patient.domain.PatientLookupPort;
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
class ListTarbilSyncLogsUseCaseTest {

    @Mock private TarbilSubmissionRepository syncLogRepository;
    @Mock private TarbilValueMappingRepository mappingRepository;
    @Mock private VaccinationLookupPort vaccinationLookupPort;
    @Mock private PatientLookupPort patientLookupPort;

    private final UUID tenantId = UUID.randomUUID();

    private TarbilSubmissionAssembler assembler() {
        return new TarbilSubmissionAssembler(vaccinationLookupPort, patientLookupPort, mappingRepository);
    }

    private TarbilSubmission pendingWith(VaccinationStatus status) {
        UUID vaccinationId = UUID.randomUUID();
        TarbilSubmission log = TarbilSubmission.queueVaccination(tenantId, UUID.randomUUID(), vaccinationId);
        when(vaccinationLookupPort.findForTarbil(vaccinationId)).thenReturn(Optional.of(new VaccinationTarbilView(
            vaccinationId, tenantId, log.getPatientId(), "Kuduz", null, LocalDate.now(), status)));
        return log;
    }

    @Test
    void should_hidePending_when_vaccinationCancelledAfterQueueing() {
        TarbilSubmission live = pendingWith(VaccinationStatus.ADMINISTERED);
        TarbilSubmission cancelled = pendingWith(VaccinationStatus.CANCELLED);
        when(syncLogRepository.findByTenantId(tenantId)).thenReturn(List.of(live, cancelled));
        lenient().when(patientLookupPort.findTarbilProfile(any())).thenReturn(Optional.empty());

        var logs = new ListTarbilSyncLogsUseCase(syncLogRepository, patientLookupPort, assembler()).execute(tenantId);

        assertThat(logs).hasSize(1);
    }
}
