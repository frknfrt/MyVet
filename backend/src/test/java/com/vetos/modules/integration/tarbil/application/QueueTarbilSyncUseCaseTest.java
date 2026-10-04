package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueTarbilSyncUseCaseTest {

    @Mock private TarbilSubmissionRepository repository;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setTenant() {
        TenantContext.set(tenantId);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void should_createPendingRow_when_vaccinationNotQueued() {
        UUID patientId = UUID.randomUUID();
        UUID vaccinationId = UUID.randomUUID();
        when(repository.findByDocumentTypeAndSourceId(TarbilDocumentType.VACCINATION, vaccinationId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        new QueueTarbilSyncUseCase(repository).queueVaccination(patientId, vaccinationId);

        ArgumentCaptor<TarbilSubmission> captor = ArgumentCaptor.forClass(TarbilSubmission.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().getSourceId()).isEqualTo(vaccinationId);
    }

    @Test
    void should_notCreateSecondRow_when_vaccinationAlreadyQueued() {
        UUID vaccinationId = UUID.randomUUID();
        TarbilSubmission existing = TarbilSubmission.queueVaccination(tenantId, UUID.randomUUID(), vaccinationId);
        when(repository.findByDocumentTypeAndSourceId(TarbilDocumentType.VACCINATION, vaccinationId)).thenReturn(Optional.of(existing));

        new QueueTarbilSyncUseCase(repository).queueVaccination(UUID.randomUUID(), vaccinationId);

        verify(repository, never()).save(any());
    }
}
