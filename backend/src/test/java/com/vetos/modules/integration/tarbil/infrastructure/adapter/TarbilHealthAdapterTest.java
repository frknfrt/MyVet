package com.vetos.modules.integration.tarbil.infrastructure.adapter;

import com.vetos.modules.integration.tarbil.domain.FailedTarbilSyncView;
import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.PatientSummary;
import com.vetos.modules.patient.domain.exception.PatientNotFoundException;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Eklenti modelinde sunucu TARBIL'e kendisi gondermez, "basarisiz senkron" yoktur. Saglik paneli bunun yerine
 * eklentiden uzun suredir gonderilmeyen (STALE_AFTER) bekleyen aktarimlari gosterir.
 */
@ExtendWith(MockitoExtension.class)
class TarbilHealthAdapterTest {

    @Mock private TarbilSubmissionRepository submissions;
    @Mock private TenantLookupPort tenantLookupPort;
    @Mock private PatientLookupPort patientLookupPort;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();

    private TarbilHealthAdapter adapter() {
        return new TarbilHealthAdapter(submissions, tenantLookupPort, patientLookupPort);
    }

    @Test
    void should_listPendingOlderThanThreeDays_withClinicAndPatientNames() {
        TarbilSubmission stale = TarbilSubmission.queueVaccination(tenantId, patientId, UUID.randomUUID());
        when(submissions.findPendingQueuedBefore(any(), eq(100))).thenReturn(List.of(stale));
        when(tenantLookupPort.findTenantName(tenantId)).thenReturn(Optional.of("Pati Veteriner"));
        when(patientLookupPort.findSummaryById(patientId)).thenReturn(new PatientSummary(patientId, "Tekir", UUID.randomUUID(), "Kedi"));

        List<FailedTarbilSyncView> views = adapter().findRecentFailed(100);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(submissions).findPendingQueuedBefore(cutoff.capture(), eq(100));
        assertThat(cutoff.getValue()).isCloseTo(Instant.now().minus(TarbilHealthAdapter.STALE_AFTER), within(Duration.ofMinutes(1)));
        assertThat(views).hasSize(1);
        FailedTarbilSyncView v = views.get(0);
        assertThat(v.syncLogId()).isEqualTo(stale.getId());
        assertThat(v.tenantName()).isEqualTo("Pati Veteriner");
        assertThat(v.patientName()).isEqualTo("Tekir");
        assertThat(v.syncType()).isEqualTo("VACCINATION");
        assertThat(v.failureReason()).contains("eklenti");
        assertThat(v.attemptCount()).isZero();
        assertThat(v.attemptedAt()).isEqualTo(stale.getQueuedAt());
    }

    @Test
    void should_fallBackToUnknownNames_when_clinicOrPatientMissing() {
        TarbilSubmission stale = TarbilSubmission.queueVaccination(tenantId, patientId, UUID.randomUUID());
        when(submissions.findPendingQueuedBefore(any(), eq(100))).thenReturn(List.of(stale));
        when(tenantLookupPort.findTenantName(tenantId)).thenReturn(Optional.empty());
        when(patientLookupPort.findSummaryById(patientId)).thenThrow(new PatientNotFoundException(patientId));

        FailedTarbilSyncView v = adapter().findRecentFailed(100).get(0);

        assertThat(v.tenantName()).isEqualTo("Bilinmeyen Klinik");
        assertThat(v.patientName()).isEqualTo("Bilinmeyen Hasta");
    }

    @Test
    void should_notLookUpPatient_when_documentHasNone() {
        TarbilSubmission receipt = TarbilSubmission.queue(tenantId, TarbilDocumentType.STOCK_RECEIPT, null, UUID.randomUUID());
        when(submissions.findPendingQueuedBefore(any(), eq(100))).thenReturn(List.of(receipt));
        when(tenantLookupPort.findTenantName(tenantId)).thenReturn(Optional.of("Pati Veteriner"));

        FailedTarbilSyncView v = adapter().findRecentFailed(100).get(0);

        assertThat(v.patientName()).isEqualTo("—");
        assertThat(v.syncType()).isEqualTo("STOCK_RECEIPT");
    }

    @Test
    void should_countStalePendingForTenant() {
        when(submissions.countPendingQueuedBefore(eq(tenantId), any())).thenReturn(3L);

        assertThat(adapter().countFailedForTenant(tenantId)).isEqualTo(3L);
    }
}
