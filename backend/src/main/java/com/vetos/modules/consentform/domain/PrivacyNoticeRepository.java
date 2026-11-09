package com.vetos.modules.consentform.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrivacyNoticeRepository {
    Optional<PrivacyNotice> find(UUID tenantId);
    boolean save(UUID tenantId, String text, long expectedVersion, UUID staffId);
    void recordDelivery(UUID tenantId, UUID ownerId, PrivacyNotice notice, UUID staffId, String clientIp);
    List<Delivery> deliveries(UUID tenantId, UUID ownerId);
    record Delivery(UUID id, long version, String text, String hash, UUID recordedBy, Instant recordedAt) {}
}
