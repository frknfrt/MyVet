package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.encounter.domain.EncounterLookupPort;
import com.vetos.modules.encounter.domain.EncounterStatus;
import com.vetos.modules.encounter.domain.EncounterSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.domain.TarbilSourceKind;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilNothingToDeductConflictException;
import com.vetos.modules.inventory.domain.PendingDeductionLine;
import com.vetos.modules.inventory.domain.PendingDeductionStatus;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.TarbilStockDeductionPort;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Muayene karti "TARBIL'den stok dus" (spec 2026-10-09 S5.1). */
@ExtendWith(MockitoExtension.class)
class QueueEncounterDeductionUseCaseTest {

    @Mock private EncounterLookupPort encounters;
    @Mock private TarbilStockDeductionPort deductions;
    @Mock private TarbilSubmissionRepository submissions;

    private final UUID tenant = UUID.randomUUID();
    private final UUID encounter = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();
    private final UUID disease = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(tenant);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private QueueEncounterDeductionUseCase useCase() {
        return new QueueEncounterDeductionUseCase(encounters, deductions, submissions);
    }

    private PendingDeductionLine line(PendingDeductionStatus status) {
        return new PendingDeductionLine(UUID.randomUUID(), UUID.randomUUID(), "Synulox", "Synulox", "S1", 1, status, null);
    }

    private void finalized() {
        when(encounters.findSummaryById(encounter)).thenReturn(new EncounterSummary(encounter, patient, UUID.randomUUID(), EncounterStatus.FINALIZED));
    }

    @Test
    void queuesAPrescriptionSubmissionForTheEncounter() {
        finalized();
        when(deductions.listForReference(StockReferenceType.ENCOUNTER, encounter)).thenReturn(List.of(line(PendingDeductionStatus.PENDING)));
        when(submissions.findByDocumentTypeAndSourceId(TarbilDocumentType.PRESCRIPTION, encounter)).thenReturn(Optional.empty());
        when(submissions.save(any())).thenAnswer(a -> a.getArgument(0));

        useCase().execute(encounter, disease);

        ArgumentCaptor<TarbilSubmission> saved = ArgumentCaptor.forClass(TarbilSubmission.class);
        verify(submissions).save(saved.capture());
        assertThat(saved.getValue().getSourceKind()).isEqualTo(TarbilSourceKind.ENCOUNTER);
        assertThat(saved.getValue().getTarbilDiseaseId()).isEqualTo(disease);
        assertThat(saved.getValue().getPatientId()).isEqualTo(patient);
        assertThat(saved.getValue().getTenantId()).isEqualTo(tenant);
    }

    @Test
    void returnsTheExistingSubmissionWhenQueuedTwice() {
        finalized();
        when(deductions.listForReference(StockReferenceType.ENCOUNTER, encounter)).thenReturn(List.of(line(PendingDeductionStatus.PENDING)));
        TarbilSubmission existing = TarbilSubmission.queuePrescription(tenant, patient, TarbilSourceKind.ENCOUNTER, encounter, disease);
        when(submissions.findByDocumentTypeAndSourceId(TarbilDocumentType.PRESCRIPTION, encounter)).thenReturn(Optional.of(existing));

        assertThat(useCase().execute(encounter, disease)).isEqualTo(existing.getId());
        verify(submissions, never()).save(any());
    }

    @Test
    void queueWithoutPendingLinesConflicts() {
        finalized();
        when(deductions.listForReference(StockReferenceType.ENCOUNTER, encounter)).thenReturn(List.of(line(PendingDeductionStatus.DEDUCTED)));

        assertThatThrownBy(() -> useCase().execute(encounter, disease)).isInstanceOf(TarbilNothingToDeductConflictException.class);
        verify(submissions, never()).save(any());
    }

    @Test
    void draftEncounterCannotBeQueued() {
        when(encounters.findSummaryById(encounter)).thenReturn(new EncounterSummary(encounter, patient, UUID.randomUUID(), EncounterStatus.DRAFT));

        assertThatThrownBy(() -> useCase().execute(encounter, disease)).isInstanceOf(TarbilNothingToDeductConflictException.class);
    }
}
