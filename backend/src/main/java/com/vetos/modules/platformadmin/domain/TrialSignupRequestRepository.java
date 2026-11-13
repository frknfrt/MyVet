package com.vetos.modules.platformadmin.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrialSignupRequestRepository {
    TrialSignupRequest save(TrialSignupRequest request);
    Optional<TrialSignupRequest> findById(UUID id);
    Optional<TrialSignupRequest> findByIdForUpdate(UUID id);
    boolean existsByPhoneAndStatus(String phoneE164, TrialSignupRequestStatus status);
    long countByPhoneAndCreatedAtAfter(String phoneE164, Instant since);
    long countByCreatedAtAfter(Instant since);
    List<TrialSignupRequest> findAllNewestFirst();
}
