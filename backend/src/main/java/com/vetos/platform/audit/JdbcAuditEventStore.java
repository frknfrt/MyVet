package com.vetos.platform.audit;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * audit_events deposu (native SQL). Tum sorgular tenant_id'yi ACIKCA alir -- RLS yok.
 * Transaction yonetimi AuditPortAdapter'da.
 */
@Repository
public class JdbcAuditEventStore implements AuditEventQuery {

    static final int MAX_SUMMARY = 500;

    private static final RowMapper<AuditEventView> MAPPER = (rs, i) -> new AuditEventView(
        rs.getObject("id", UUID.class),
        rs.getTimestamp("occurred_at").toInstant(),
        rs.getString("actor_type"),
        rs.getObject("actor_id", UUID.class),
        rs.getString("actor_name"),
        rs.getString("actor_role"),
        rs.getString("impersonated_by"),
        rs.getString("channel"),
        rs.getString("action"),
        AuditAction.labelOf(rs.getString("action")),
        rs.getString("target_type"),
        rs.getObject("target_id", UUID.class),
        rs.getString("target_label"),
        rs.getString("summary"),
        rs.getString("ip_address")
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAuditEventStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tenantId, Instant occurredAt, AuditActor actor, AuditRecord record, String ip, String requestId) {
        String summary = record.summary();
        if (summary != null && summary.length() > MAX_SUMMARY) {
            summary = summary.substring(0, MAX_SUMMARY);
        }
        jdbc.update("""
            INSERT INTO audit_events (id, tenant_id, occurred_at, actor_type, actor_id, actor_name, actor_role,
                impersonated_by, channel, action, target_type, target_id, target_label, summary, ip_address, request_id)
            VALUES (:id, :tenantId, :occurredAt, :actorType, :actorId, :actorName, :actorRole,
                :impersonatedBy, :channel, :action, :targetType, :targetId, :targetLabel, :summary, :ip, :requestId)
            """, new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("tenantId", tenantId)
            .addValue("occurredAt", Timestamp.from(occurredAt))
            .addValue("actorType", actor.type().name())
            .addValue("actorId", actor.id())
            .addValue("actorName", actor.name())
            .addValue("actorRole", actor.role())
            .addValue("impersonatedBy", actor.impersonatedBy())
            .addValue("channel", actor.channel().name())
            .addValue("action", record.action().name())
            .addValue("targetType", record.targetType())
            .addValue("targetId", record.targetId())
            .addValue("targetLabel", record.targetLabel())
            .addValue("summary", summary)
            .addValue("ip", ip)
            .addValue("requestId", requestId));
    }

    @Override
    public AuditEventPage list(UUID tenantId, AuditEventFilter filter, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_events WHERE tenant_id = :tenantId");
        MapSqlParameterSource p = new MapSqlParameterSource("tenantId", tenantId);
        if (filter.from() != null) {
            sql.append(" AND occurred_at >= :from");
            p.addValue("from", Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" AND occurred_at < :to");
            p.addValue("to", Timestamp.from(filter.to()));
        }
        if (filter.action() != null) {
            sql.append(" AND action = :action");
            p.addValue("action", filter.action().name());
        }
        if (filter.actorId() != null) {
            sql.append(" AND actor_id = :actorId");
            p.addValue("actorId", filter.actorId());
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset");
        p.addValue("limit", size + 1).addValue("offset", (long) page * size);
        List<AuditEventView> rows = jdbc.query(sql.toString(), p, MAPPER);
        boolean hasMore = rows.size() > size;
        return new AuditEventPage(hasMore ? rows.subList(0, size) : rows, page, size, hasMore);
    }
}
