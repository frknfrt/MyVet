package com.vetos.modules.imaging.infrastructure.persistence;

import com.vetos.modules.imaging.domain.ImagingRecordFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ImagingRecordFileJpaRepository extends JpaRepository<ImagingRecordFile, UUID> {
    List<ImagingRecordFile> findByImagingRecordId(UUID imagingRecordId);
}
