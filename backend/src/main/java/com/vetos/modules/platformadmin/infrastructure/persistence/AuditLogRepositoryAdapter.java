package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.AuditLogEntry;
import com.vetos.modules.platformadmin.domain.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class AuditLogRepositoryAdapter implements AuditLogRepository {

    private final AuditLogJpaRepository jpaRepository;

    @Override
    public AuditLogEntry save(AuditLogEntry entry) { return jpaRepository.save(entry); }

    @Override
    public List<AuditLogEntry> findTop200ByOrderByCreatedAtDesc() { return jpaRepository.findTop200ByOrderByCreatedAtDesc(); }
}
