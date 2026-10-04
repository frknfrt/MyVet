package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.AuditLogEntry;
import com.vetos.modules.platformadmin.domain.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListAuditLogUseCase {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public List<AuditLogEntry> execute() {
        return auditLogRepository.findTop200ByOrderByCreatedAtDesc();
    }
}
