package com.vetos.modules.notification.infrastructure.persistence;

import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface NotificationLogJpaRepository extends JpaRepository<NotificationLog, UUID> {
    List<NotificationLog> findByTenantId(UUID tenantId);
    boolean existsByRelatedEntityIdAndNotificationType(UUID relatedEntityId, NotificationType notificationType);

    @Query(value = """
        SELECT * FROM notification_log
        WHERE status = 'FAILED' AND next_retry_at IS NOT NULL AND next_retry_at <= :now
        ORDER BY next_retry_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<NotificationLog> claimDueForRetry(@Param("now") Instant now, @Param("limit") int limit);
}
