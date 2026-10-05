package com.vetos.modules.integration.tarbil.infrastructure.adapter;

import com.vetos.modules.integration.tarbil.domain.FailedTarbilSyncView;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncType;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.PatientSummary;
import com.vetos.modules.patient.domain.exception.PatientNotFoundException;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TarbilHealthAdapterTest {

    @Mock private TarbilSyncLogRepository tarbilSyncLogRepository;
    @Mock private TenantLookupPort tenantLookupPort;
    @Mock private PatientLookupPort patientLookupPort;

    private TarbilHealthAdapter adapter() {
        return new TarbilHealthAdapter(tarbilSyncLogRepository, tenantLookupPort, patientLookupPort);
    }

    @Test
    void should_mapTenantNameAndPatientName_when_findRecentFailed() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        TarbilSyncLog log = TarbilSyncLog.queue(tenantId, patientId, TarbilSyncType.VACCINATION, "{}");
        log.markFailed("Bakanlik API zaman asimi", Instant.now().plusSeconds(60));
        when(tarbilSyncLogRepository.findRecentByStatus(TarbilSyncStatus.FAILED, 100)).thenReturn(List.of(log));
        when(tenantLookupPort.findTenantName(tenantId)).thenReturn(Optional.of("Pati Veteriner"));
        when(patientLookupPort.findSummaryById(patientId)).thenReturn(new PatientSummary(patientId, "Tekir", UUID.randomUUID(), "Kedi"));

        List<FailedTarbilSyncView> views = adapter().findRecentFailed(100);

        assertThat(views).hasSize(1);
        FailedTarbilSyncView view = views.get(0);
        assertThat(view.tenantName()).isEqualTo("Pati Veteriner");
        assertThat(view.patientName()).isEqualTo("Tekir");
        assertThat(view.failureReason()).isEqualTo("Bakanlik API zaman asimi");
    }

    @Test
    void should_fallbackToUnknownClinic_when_tenantNameMissing() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        TarbilSyncLog log = TarbilSyncLog.queue(tenantId, patientId, TarbilSyncType.IDENTIFICATION, "{}");
        log.markFailed("hata", Instant.now());
        when(tarbilSyncLogRepository.findRecentByStatus(TarbilSyncStatus.FAILED, 100)).thenReturn(List.of(log));
        when(tenantLookupPort.findTenantName(tenantId)).thenReturn(Optional.empty());
        when(patientLookupPort.findSummaryById(patientId)).thenReturn(new PatientSummary(patientId, "Boncuk", UUID.randomUUID(), "Kopek"));

        List<FailedTarbilSyncView> views = adapter().findRecentFailed(100);

        assertThat(views.get(0).tenantName()).isEqualTo("Bilinmeyen Klinik");
    }

    @Test
    void should_fallbackToUnknownPatient_when_patientDeleted() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        TarbilSyncLog log = TarbilSyncLog.queue(tenantId, patientId, TarbilSyncType.TREATMENT, "{}");
        log.markFailed("hata", Instant.now());
        when(tarbilSyncLogRepository.findRecentByStatus(TarbilSyncStatus.FAILED, 100)).thenReturn(List.of(log));
        when(tenantLookupPort.findTenantName(tenantId)).thenReturn(Optional.of("Pati Veteriner"));
        when(patientLookupPort.findSummaryById(patientId)).thenThrow(new PatientNotFoundException(patientId));

        List<FailedTarbilSyncView> views = adapter().findRecentFailed(100);

        assertThat(views.get(0).patientName()).isEqualTo("Bilinmeyen Hasta");
    }

    @Test
    void should_countOnlyFailed_when_countFailedForTenant() {
        UUID tenantId = UUID.randomUUID();
        TarbilSyncLog failed = TarbilSyncLog.queue(tenantId, UUID.randomUUID(), TarbilSyncType.VACCINATION, "{}");
        failed.markFailed("hata", Instant.now());
        TarbilSyncLog synced = TarbilSyncLog.queue(tenantId, UUID.randomUUID(), TarbilSyncType.VACCINATION, "{}");
        synced.markSynced();
        when(tarbilSyncLogRepository.findByTenantId(tenantId)).thenReturn(List.of(failed, synced));

        long count = adapter().countFailedForTenant(tenantId);

        assertThat(count).isEqualTo(1);
    }
}
