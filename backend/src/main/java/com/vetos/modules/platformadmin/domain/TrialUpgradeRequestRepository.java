package com.vetos.modules.platformadmin.domain;

import java.util.Optional;
import java.util.UUID;

public interface TrialUpgradeRequestRepository {
    TrialUpgradeRequest save(TrialUpgradeRequest request);
    Optional<TrialUpgradeRequest> findById(UUID id);
    Optional<TrialUpgradeRequest> findByIdForUpdate(UUID id);
}
