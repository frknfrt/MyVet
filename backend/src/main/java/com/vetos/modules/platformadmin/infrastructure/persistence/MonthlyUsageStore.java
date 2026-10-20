package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.platform.quota.UsageKind;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * tenant_monthly_usage sayaci. Her yazma REQUIRES_NEW: uzun AI cagrisi boyunca satir kilidi
 * tutulmaz, ayrilan hak esZamanli isteklerce hemen gorulur. Kota kontrolu + artirma TEK
 * UPDATE'te (WHERE used + n <= quota): READ COMMITTED'da esZamanli UPDATE'ler satirda sirayla
 * bekler ve kosulu yeni degerle yeniden degerlendirir -- kota delinmez.
 */
@Repository
public class MonthlyUsageStore {

    private final NamedParameterJdbcTemplate jdbc;

    public MonthlyUsageStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryIncrement(UUID tenantId, String period, UsageKind kind, int amount, int quota) {
        MapSqlParameterSource p = key(tenantId, period, kind).addValue("n", amount).addValue("quota", quota);
        jdbc.update("""
            INSERT INTO tenant_monthly_usage (tenant_id, period, kind, used)
            VALUES (:tenantId, :period, :kind, 0)
            ON CONFLICT DO NOTHING
            """, p);
        return jdbc.update("""
            UPDATE tenant_monthly_usage SET used = used + :n
            WHERE tenant_id = :tenantId AND period = :period AND kind = :kind AND used + :n <= :quota
            """, p) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increment(UUID tenantId, String period, UsageKind kind, int amount) {
        jdbc.update("""
            INSERT INTO tenant_monthly_usage (tenant_id, period, kind, used)
            VALUES (:tenantId, :period, :kind, :n)
            ON CONFLICT (tenant_id, period, kind) DO UPDATE SET used = tenant_monthly_usage.used + EXCLUDED.used
            """, key(tenantId, period, kind).addValue("n", amount));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decrement(UUID tenantId, String period, UsageKind kind, int amount) {
        jdbc.update("""
            UPDATE tenant_monthly_usage SET used = GREATEST(used - :n, 0)
            WHERE tenant_id = :tenantId AND period = :period AND kind = :kind
            """, key(tenantId, period, kind).addValue("n", amount));
    }

    public int used(UUID tenantId, String period, UsageKind kind) {
        List<Integer> rows = jdbc.queryForList("""
            SELECT used FROM tenant_monthly_usage
            WHERE tenant_id = :tenantId AND period = :period AND kind = :kind
            """, key(tenantId, period, kind), Integer.class);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private static MapSqlParameterSource key(UUID tenantId, String period, UsageKind kind) {
        return new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("period", period)
            .addValue("kind", kind.name());
    }
}
