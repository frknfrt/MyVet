package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.TrialSignupRequest;
import com.vetos.modules.platformadmin.domain.TrialSignupRequestRepository;
import com.vetos.modules.platformadmin.domain.TrialSignupRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TrialSignupRequestRepositoryAdapter implements TrialSignupRequestRepository {

    private final TrialSignupRequestJpaRepository jpaRepository;

    @Override
    public TrialSignupRequest save(TrialSignupRequest request) { return jpaRepository.save(request); }

    @Override
    public Optional<TrialSignupRequest> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public Optional<TrialSignupRequest> findByIdForUpdate(UUID id) { return jpaRepository.findByIdForUpdate(id); }

    @Override
    public boolean existsByPhoneAndStatus(String phoneE164, TrialSignupRequestStatus status) {
        return jpaRepository.existsByPhoneAndStatus(phoneE164, status);
    }

    @Override
    public long countByPhoneAndCreatedAtAfter(String phoneE164, Instant since) {
        return jpaRepository.countByPhoneAndCreatedAtAfter(phoneE164, since);
    }

    @Override
    public List<TrialSignupRequest> findAllNewestFirst() { return jpaRepository.findAllByOrderByCreatedAtDesc(); }
}
