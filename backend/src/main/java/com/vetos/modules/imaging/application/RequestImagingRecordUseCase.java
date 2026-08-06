package com.vetos.modules.imaging.application;

import com.vetos.modules.imaging.application.dto.RequestImagingRecordCommand;
import com.vetos.modules.imaging.domain.ImagingRecord;
import com.vetos.modules.imaging.domain.ImagingRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RequestImagingRecordUseCase {

    private final ImagingRecordRepository imagingRecordRepository;

    @Transactional
    public UUID execute(RequestImagingRecordCommand command) {
        ImagingRecord record = ImagingRecord.request(
            command.tenantId(), command.patientId(), command.orderingStaffId(),
            command.modality(), command.bodyRegion(), command.notes()
        );
        return imagingRecordRepository.save(record).getId();
    }
}
