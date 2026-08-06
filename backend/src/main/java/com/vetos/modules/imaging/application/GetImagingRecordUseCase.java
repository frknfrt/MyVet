package com.vetos.modules.imaging.application;

import com.vetos.modules.imaging.application.dto.ImagingRecordDetail;
import com.vetos.modules.imaging.application.dto.ImagingRecordFileMeta;
import com.vetos.modules.imaging.domain.ImagingRecord;
import com.vetos.modules.imaging.domain.ImagingRecordFileRepository;
import com.vetos.modules.imaging.domain.ImagingRecordRepository;
import com.vetos.modules.imaging.domain.exception.ImagingRecordNotFoundException;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetImagingRecordUseCase {

    private final ImagingRecordRepository imagingRecordRepository;
    private final ImagingRecordFileRepository imagingRecordFileRepository;
    private final PatientLookupPort patientLookupPort;
    private final OwnerLookupPort ownerLookupPort;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional(readOnly = true)
    public ImagingRecordDetail execute(UUID imagingRecordId) {
        ImagingRecord record = imagingRecordRepository.findById(imagingRecordId)
            .orElseThrow(() -> new ImagingRecordNotFoundException(imagingRecordId));

        var patient = patientLookupPort.findSummaryById(record.getPatientId());
        var owner = ownerLookupPort.findSummaryById(patient.ownerId());
        String staffName = record.getOrderingStaffId() == null
            ? null
            : staffUserLookupPort.findSummaryById(record.getOrderingStaffId()).fullName();

        var files = imagingRecordFileRepository.findByImagingRecordId(imagingRecordId).stream()
            .map(f -> new ImagingRecordFileMeta(f.getId(), f.getFileName(), f.getContentType(), f.getFileSize(), f.getUploadedAt()))
            .toList();

        return new ImagingRecordDetail(
            record.getId(), patient.id(), patient.name(), owner.id(), owner.fullName(),
            record.getModality(), record.getBodyRegion(), record.getStatus(),
            record.getRequestedAt(), record.getResultedAt(), staffName,
            record.getFindings(), record.getNotes(), files
        );
    }
}
