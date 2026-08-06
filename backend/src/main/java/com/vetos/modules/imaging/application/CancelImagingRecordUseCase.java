package com.vetos.modules.imaging.application;

import com.vetos.modules.imaging.domain.ImagingRecord;
import com.vetos.modules.imaging.domain.ImagingRecordRepository;
import com.vetos.modules.imaging.domain.exception.ImagingRecordNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CancelImagingRecordUseCase {

    private final ImagingRecordRepository imagingRecordRepository;

    @Transactional
    public void execute(UUID imagingRecordId) {
        ImagingRecord record = imagingRecordRepository.findById(imagingRecordId)
            .orElseThrow(() -> new ImagingRecordNotFoundException(imagingRecordId));
        record.cancel();
        imagingRecordRepository.save(record);
    }
}
