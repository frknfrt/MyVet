package com.vetos.platform.audit;

import java.util.List;

public record AuditEventPage(List<AuditEventView> items, int page, int size, boolean hasMore) {
}
