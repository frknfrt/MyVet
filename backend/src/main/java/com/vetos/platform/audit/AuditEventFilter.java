package com.vetos.platform.audit;

import java.time.Instant;
import java.util.UUID;

/** Liste filtresi; alanlarin hepsi null olabilir. */
public record AuditEventFilter(Instant from, Instant to, AuditAction action, UUID actorId) {
}
