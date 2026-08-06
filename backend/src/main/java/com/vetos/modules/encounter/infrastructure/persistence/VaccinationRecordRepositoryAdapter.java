package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class VaccinationRecordRepositoryAdapter implements VaccinationRecordRepository {

    private final VaccinationRecordJpaRepository jpaRepository;

    @Override
    public VaccinationRecord save(VaccinationRecord record) { return jpaRepository.save(record); }

    @Override
    public Optional<VaccinationRecord> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<VaccinationRecord> findByPatientId(UUID patientId) { return jpaRepository.findByPatientId(patientId); }

    @Override
    public List<VaccinationRecord> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }
}
