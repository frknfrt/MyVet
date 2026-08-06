package com.vetos.modules.lab.application;

import com.vetos.modules.lab.application.dto.LabResultDetail;
import com.vetos.modules.lab.application.dto.LabResultFileMeta;
import com.vetos.modules.lab.application.dto.LabResultItemDetail;
import com.vetos.modules.lab.domain.LabResult;
import com.vetos.modules.lab.domain.LabResultFileRepository;
import com.vetos.modules.lab.domain.LabResultItemRepository;
import com.vetos.modules.lab.domain.LabResultRepository;
import com.vetos.modules.lab.domain.exception.LabResultNotFoundException;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetLabResultUseCase {

    private final LabResultRepository labResultRepository;
    private final LabResultItemRepository labResultItemRepository;
    private final LabResultFileRepository labResultFileRepository;
    private final PatientLookupPort patientLookupPort;
    private final OwnerLookupPort ownerLookupPort;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional(readOnly = true)
    public LabResultDetail execute(UUID labResultId) {
        LabResult result = labResultRepository.findById(labResultId)
            .orElseThrow(() -> new LabResultNotFoundException(labResultId));

        var patient = patientLookupPort.findSummaryById(result.getPatientId());
        var owner = ownerLookupPort.findSummaryById(patient.ownerId());
        String staffName = result.getOrderingStaffId() == null
            ? null
            : staffUserLookupPort.findSummaryById(result.getOrderingStaffId()).fullName();

        var items = labResultItemRepository.findByLabResultId(labResultId).stream()
            .map(i -> new LabResultItemDetail(i.getId(), i.getParameterName(), i.getValue(), i.getUnit(), i.getReferenceRange(), i.getFlag()))
            .toList();

        var files = labResultFileRepository.findByLabResultId(labResultId).stream()
            .map(f -> new LabResultFileMeta(f.getId(), f.getFileName(), f.getContentType(), f.getFileSize(), f.getUploadedAt()))
            .toList();

        return new LabResultDetail(
            result.getId(), patient.id(), patient.name(), owner.id(), owner.fullName(),
            result.getTestName(), result.getStatus(), result.getRequestedAt(), result.getResultedAt(), staffName,
            result.getResultSummary(), result.getNotes(), items, files
        );
    }
}
