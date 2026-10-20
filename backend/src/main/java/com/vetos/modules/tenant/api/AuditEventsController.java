package com.vetos.modules.tenant.api;

import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditEventFilter;
import com.vetos.platform.audit.AuditEventPage;
import com.vetos.platform.audit.AuditEventQuery;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Klinik yoneticisi: kendi klinigin islem gecmisi (spec 2026-10-15 §7). Kiraci yalnizca TenantContext'ten. */
@RestController
@RequestMapping("/api/v1/audit-events")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditEventsController {

    private final AuditEventQuery auditEventQuery;

    @GetMapping
    public AuditEventPage list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) AuditAction action,
        @RequestParam(required = false) UUID actorId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size
    ) {
        return auditEventQuery.list(TenantContext.current(), new AuditEventFilter(from, to, action, actorId), page, size);
    }

    @GetMapping("/actions")
    public List<ActionOption> actions() {
        return Arrays.stream(AuditAction.values()).map(a -> new ActionOption(a.name(), a.label())).toList();
    }

    public record ActionOption(String code, String label) {}
}
