package com.vetos.modules.imaging.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImagingRecordFileRepository {
    ImagingRecordFile save(ImagingRecordFile file);
    Optional<ImagingRecordFile> findById(UUID id);
    List<ImagingRecordFile> findByImagingRecordId(UUID imagingRecordId);
}
