package com.vetos.modules.patient.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentRecordRepository {
    ConsentRecord save(ConsentRecord consentRecord);
    Optional<ConsentRecord> findById(UUID id);
    List<ConsentRecord> findByOwnerId(UUID ownerId);
}
