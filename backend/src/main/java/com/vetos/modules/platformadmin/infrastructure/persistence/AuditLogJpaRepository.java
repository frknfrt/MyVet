package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AuditLogJpaRepository extends JpaRepository<AuditLogEntry, UUID> {
    List<AuditLogEntry> findTop200ByOrderByCreatedAtDesc();
}
