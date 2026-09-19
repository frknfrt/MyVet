package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilSyncLogRepositoryAdapter implements TarbilSyncLogRepository {

    private final TarbilSyncLogJpaRepository jpaRepository;

    @Override
    public TarbilSyncLog save(TarbilSyncLog log) { return jpaRepository.save(log); }

    @Override
    public Optional<TarbilSyncLog> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<TarbilSyncLog> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }

    @Override
    public List<TarbilSyncLog> findByPatientId(UUID patientId) { return jpaRepository.findByPatientId(patientId); }

    @Override
    public List<TarbilSyncLog> claimDueForRetry(Instant now, int limit) {
        return jpaRepository.claimDueForRetry(now, limit);
    }
}
