package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilSyncLogSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.patient.domain.PatientLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListTarbilSyncLogsUseCase {

    private final TarbilSyncLogRepository tarbilSyncLogRepository;
    private final PatientLookupPort patientLookupPort;

    @Transactional(readOnly = true)
    public List<TarbilSyncLogSummary> execute(UUID tenantId) {
        return tarbilSyncLogRepository.findByTenantId(tenantId).stream()
            .map(log -> new TarbilSyncLogSummary(
                log.getId(), log.getPatientId(), patientLookupPort.findSummaryById(log.getPatientId()).name(),
                log.getSyncType(), log.getStatus(), log.getAttemptedAt()
            ))
            .sorted(Comparator.comparing(TarbilSyncLogSummary::attemptedAt).reversed())
            .toList();
    }
}
