package com.vetos.modules.imaging.application;

import com.vetos.modules.imaging.application.dto.CompleteImagingRecordCommand;
import com.vetos.modules.imaging.domain.ImagingRecord;
import com.vetos.modules.imaging.domain.ImagingRecordRepository;
import com.vetos.modules.imaging.domain.exception.ImagingRecordNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompleteImagingRecordUseCase {

    private final ImagingRecordRepository imagingRecordRepository;

    @Transactional
    public void execute(CompleteImagingRecordCommand command) {
        ImagingRecord record = imagingRecordRepository.findById(command.imagingRecordId())
            .orElseThrow(() -> new ImagingRecordNotFoundException(command.imagingRecordId()));
        record.complete(command.findings());
        imagingRecordRepository.save(record);
    }
}
