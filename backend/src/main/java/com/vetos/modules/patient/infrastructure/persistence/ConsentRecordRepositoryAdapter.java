package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.ConsentRecord;
import com.vetos.modules.patient.domain.ConsentRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class ConsentRecordRepositoryAdapter implements ConsentRecordRepository {

    private final ConsentRecordJpaRepository jpaRepository;

    @Override
    public ConsentRecord save(ConsentRecord consentRecord) { return jpaRepository.save(consentRecord); }

    @Override
    public Optional<ConsentRecord> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<ConsentRecord> findByOwnerId(UUID ownerId) { return jpaRepository.findByOwnerId(ownerId); }
}
