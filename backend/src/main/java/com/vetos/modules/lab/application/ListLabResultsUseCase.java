package com.vetos.modules.lab.application;

import com.vetos.modules.lab.application.dto.LabResultSummary;
import com.vetos.modules.lab.domain.LabResult;
import com.vetos.modules.lab.domain.LabResultRepository;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListLabResultsUseCase {

    private final LabResultRepository labResultRepository;
    private final PatientLookupPort patientLookupPort;
    private final OwnerLookupPort ownerLookupPort;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional(readOnly = true)
    public List<LabResultSummary> execute(UUID tenantId) {
        return labResultRepository.findByTenantId(tenantId).stream()
            .map(this::toSummary)
            .sorted(Comparator.comparing(LabResultSummary::requestedAt).reversed())
            .toList();
    }

    private LabResultSummary toSummary(LabResult result) {
        var patient = patientLookupPort.findSummaryById(result.getPatientId());
        var owner = ownerLookupPort.findSummaryById(patient.ownerId());
        String staffName = result.getOrderingStaffId() == null
            ? null
            : staffUserLookupPort.findSummaryById(result.getOrderingStaffId()).fullName();

        return new LabResultSummary(
            result.getId(), patient.id(), patient.name(), owner.id(), owner.fullName(),
            result.getTestName(), result.getStatus(), result.getRequestedAt(), result.getResultedAt(), staffName
        );
    }
}
