package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import com.vetos.modules.integration.tarbil.application.dto.TarbilSyncLogSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.PatientTarbilProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListTarbilSyncLogsUseCase {

    private final TarbilSubmissionRepository tarbilSyncLogRepository;
    private final PatientLookupPort patientLookupPort;
    private final TarbilSubmissionAssembler assembler;

    @Transactional(readOnly = true)
    public List<TarbilSyncLogSummary> execute(UUID tenantId) {
        return tarbilSyncLogRepository.findByTenantId(tenantId).stream()
            .filter(assembler::isVisible)
            .map(log -> {
                var vaccination = assembler.liveVaccination(log);
                return new TarbilSyncLogSummary(
                    log.getId(), log.getPatientId(),
                    patientLookupPort.findTarbilProfile(log.getPatientId()).map(PatientTarbilProfile::name).orElse("—"),
                    vaccination.map(VaccinationTarbilView::vaccineName).orElse("—"),
                    vaccination.map(VaccinationTarbilView::administeredDate).orElse(null),
                    log.getStatus(), log.getQueuedAt(), log.getSubmittedAt(),
                    log.getConfirmationMethod(), log.getTarbilReference(), log.getDismissedReason()
                );
            })
            .sorted(Comparator.comparing(TarbilSyncLogSummary::queuedAt).reversed())
            .toList();
    }
}
