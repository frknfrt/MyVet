package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.AuditLogEntryResponse;
import com.vetos.modules.platformadmin.application.ListAuditLogUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Platform admin "Denetim Kaydı" paneli -- bkz. AuditLogEntry. */
@RestController
@RequestMapping("/api/v1/platform-admin/audit-log")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AuditLogController {

    private final ListAuditLogUseCase listAuditLogUseCase;

    @GetMapping
    public List<AuditLogEntryResponse> list() {
        return listAuditLogUseCase.execute().stream().map(AuditLogEntryResponse::from).toList();
    }
}
