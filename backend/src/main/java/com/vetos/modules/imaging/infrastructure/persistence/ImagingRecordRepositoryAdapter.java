package com.vetos.modules.imaging.infrastructure.persistence;

import com.vetos.modules.imaging.domain.ImagingRecord;
import com.vetos.modules.imaging.domain.ImagingRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class ImagingRecordRepositoryAdapter implements ImagingRecordRepository {

    private final ImagingRecordJpaRepository jpaRepository;

    @Override
    public ImagingRecord save(ImagingRecord record) { return jpaRepository.save(record); }

    @Override
    public Optional<ImagingRecord> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<ImagingRecord> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }

    @Override
    public List<ImagingRecord> findByPatientId(UUID patientId) { return jpaRepository.findByPatientId(patientId); }
}
