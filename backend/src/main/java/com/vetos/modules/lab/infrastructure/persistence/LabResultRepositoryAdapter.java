package com.vetos.modules.lab.infrastructure.persistence;

import com.vetos.modules.lab.domain.LabResult;
import com.vetos.modules.lab.domain.LabResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class LabResultRepositoryAdapter implements LabResultRepository {

    private final LabResultJpaRepository jpaRepository;

    @Override
    public LabResult save(LabResult labResult) { return jpaRepository.save(labResult); }

    @Override
    public Optional<LabResult> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<LabResult> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }

    @Override
    public List<LabResult> findByPatientId(UUID patientId) { return jpaRepository.findByPatientId(patientId); }
}
