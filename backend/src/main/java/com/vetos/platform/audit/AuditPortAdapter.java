package com.vetos.platform.audit;

import com.vetos.platform.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.UUID;

@Component
class AuditPortAdapter implements AuditPort {

    private final JdbcAuditEventStore store;
    private final AuditActorResolver resolver;
    private final AuditActorNames names;
    private final Clock clock;

    @Autowired
    AuditPortAdapter(JdbcAuditEventStore store, AuditActorResolver resolver, AuditActorNames names) {
        this(store, resolver, names, Clock.systemUTC());
    }

    AuditPortAdapter(JdbcAuditEventStore store, AuditActorResolver resolver, AuditActorNames names, Clock clock) {
        this.store = store;
        this.resolver = resolver;
        this.names = names;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(AuditRecord record) {
        write(record, resolver.current());
    }

    @Override
    @Transactional
    public void record(AuditRecord record, AuditActor actor) {
        write(record, actor);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStandalone(AuditRecord record, AuditActor actor) {
        write(record, actor);
    }

    private void write(AuditRecord record, AuditActor actor) {
        UUID tenantId = record.tenantId() != null ? record.tenantId() : TenantContext.currentOrNull();
        if (tenantId == null) {
            throw new IllegalStateException("Denetim kaydi icin kiraci yok: " + record.action());
        }
        AuditActor named = actor;
        if (actor.type() == AuditActor.ActorType.STAFF && actor.name() == null && actor.id() != null) {
            named = new AuditActor(actor.type(), actor.id(), names.staffName(tenantId, actor.id()), actor.role(),
                actor.impersonatedBy(), actor.channel());
        }
        store.insert(UUID.randomUUID(), tenantId, clock.instant(), named, record, clientIp(), MDC.get("requestId"));
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
