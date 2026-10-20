package com.vetos.platform.audit;

import java.util.UUID;

/** Klinik denetim kaydi okuma. tenantId her zaman acikca verilir. */
public interface AuditEventQuery {

    AuditEventPage list(UUID tenantId, AuditEventFilter filter, int page, int size);
}
