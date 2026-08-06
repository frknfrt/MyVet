package com.vetos.modules.lab.infrastructure.persistence;

import com.vetos.modules.lab.domain.LabResultFile;
import com.vetos.modules.lab.domain.LabResultFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class LabResultFileRepositoryAdapter implements LabResultFileRepository {

    private final LabResultFileJpaRepository jpaRepository;

    @Override
    public LabResultFile save(LabResultFile file) { return jpaRepository.save(file); }

    @Override
    public Optional<LabResultFile> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<LabResultFile> findByLabResultId(UUID labResultId) { return jpaRepository.findByLabResultId(labResultId); }
}
