package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterInventoryUsage;
import com.vetos.modules.encounter.domain.EncounterInventoryUsageRepository;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.EncounterStatus;
import com.vetos.modules.encounter.domain.Prescription;
import com.vetos.modules.encounter.domain.PrescriptionRepository;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.encounter.domain.exception.EncounterNotCancellableConflictException;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CancelEncounterUseCaseTest {

    @Mock private EncounterRepository encounters;
    @Mock private EncounterInventoryUsageRepository usages;
    @Mock private PrescriptionRepository prescriptions;
    @Mock private VaccinationRecordRepository vaccinations;
    @Mock private AuditPort auditPort;

    private final UUID id = UUID.randomUUID();
    private Encounter encounter;

    private CancelEncounterUseCase useCase() {
        return new CancelEncounterUseCase(encounters, usages, prescriptions, vaccinations, auditPort);
    }

    @BeforeEach
    void setUp() {
        encounter = Encounter.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        ReflectionTestUtils.setField(encounter, "id", id);
        when(encounters.findById(id)).thenReturn(Optional.of(encounter));
        when(usages.findByEncounterId(id)).thenReturn(List.of());
        when(prescriptions.findByEncounterId(id)).thenReturn(List.of());
        when(vaccinations.existsByEncounterId(id)).thenReturn(false);
    }

    @Test
    void should_cancelDraftAndRecordAudit_when_nothingIsLinked() {
        useCase().execute(id);

        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.CANCELLED);
        verify(encounters).save(encounter);
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.ENCOUNTER_CANCELLED);
        assertThat(captor.getValue().targetType()).isEqualTo("ENCOUNTER");
        assertThat(captor.getValue().targetId()).isEqualTo(id);
    }

    @Test
    void should_reject_when_materialsWereUsed() {
        when(usages.findByEncounterId(id)).thenReturn(List.of(mock(EncounterInventoryUsage.class)));

        assertThatThrownBy(() -> useCase().execute(id))
            .isInstanceOf(EncounterNotCancellableConflictException.class)
            .hasMessageContaining("malzeme");
        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.DRAFT);
        verify(encounters, never()).save(any());
        verifyNoInteractions(auditPort);
    }

    @Test
    void should_reject_when_anActivePrescriptionExists() {
        Prescription active = Prescription.issue(UUID.randomUUID(), UUID.randomUUID(), id, UUID.randomUUID(), false);
        when(prescriptions.findByEncounterId(id)).thenReturn(List.of(active));

        assertThatThrownBy(() -> useCase().execute(id))
            .isInstanceOf(EncounterNotCancellableConflictException.class)
            .hasMessageContaining("reçete");
        verifyNoInteractions(auditPort);
    }

    @Test
    void should_allow_when_theOnlyPrescriptionWasCancelled() {
        Prescription cancelled = Prescription.issue(UUID.randomUUID(), UUID.randomUUID(), id, UUID.randomUUID(), false);
        cancelled.cancel();
        when(prescriptions.findByEncounterId(id)).thenReturn(List.of(cancelled));

        useCase().execute(id);

        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.CANCELLED);
    }

    @Test
    void should_reject_when_aVaccinationIsLinked() {
        when(vaccinations.existsByEncounterId(id)).thenReturn(true);

        assertThatThrownBy(() -> useCase().execute(id))
            .isInstanceOf(EncounterNotCancellableConflictException.class)
            .hasMessageContaining("aşı");
        verifyNoInteractions(auditPort);
    }

    @Test
    void should_throwNotFound_when_encounterMissing() {
        UUID missing = UUID.randomUUID();
        when(encounters.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(missing)).isInstanceOf(EncounterNotFoundException.class);
        verifyNoInteractions(auditPort);
    }
}
