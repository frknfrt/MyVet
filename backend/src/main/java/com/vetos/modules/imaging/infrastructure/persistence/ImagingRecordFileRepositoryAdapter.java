package com.vetos.modules.imaging.infrastructure.persistence;

import com.vetos.modules.imaging.domain.ImagingRecordFile;
import com.vetos.modules.imaging.domain.ImagingRecordFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class ImagingRecordFileRepositoryAdapter implements ImagingRecordFileRepository {

    private final ImagingRecordFileJpaRepository jpaRepository;

    @Override
    public ImagingRecordFile save(ImagingRecordFile file) { return jpaRepository.save(file); }

    @Override
    public Optional<ImagingRecordFile> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<ImagingRecordFile> findByImagingRecordId(UUID imagingRecordId) { return jpaRepository.findByImagingRecordId(imagingRecordId); }
}
