package com.vetos.modules.imaging.application;

import com.vetos.modules.imaging.application.dto.ImagingRecordSummary;
import com.vetos.modules.imaging.domain.ImagingRecord;
import com.vetos.modules.imaging.domain.ImagingRecordRepository;
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
public class ListImagingRecordsUseCase {

    private final ImagingRecordRepository imagingRecordRepository;
    private final PatientLookupPort patientLookupPort;
    private final OwnerLookupPort ownerLookupPort;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional(readOnly = true)
    public List<ImagingRecordSummary> execute(UUID tenantId) {
        return imagingRecordRepository.findByTenantId(tenantId).stream()
            .map(this::toSummary)
            .sorted(Comparator.comparing(ImagingRecordSummary::requestedAt).reversed())
            .toList();
    }

    private ImagingRecordSummary toSummary(ImagingRecord record) {
        var patient = patientLookupPort.findSummaryById(record.getPatientId());
        var owner = ownerLookupPort.findSummaryById(patient.ownerId());
        String staffName = record.getOrderingStaffId() == null
            ? null
            : staffUserLookupPort.findSummaryById(record.getOrderingStaffId()).fullName();

        return new ImagingRecordSummary(
            record.getId(), patient.id(), patient.name(), owner.id(), owner.fullName(),
            record.getModality(), record.getBodyRegion(), record.getStatus(),
            record.getRequestedAt(), record.getResultedAt(), staffName
        );
    }
}
