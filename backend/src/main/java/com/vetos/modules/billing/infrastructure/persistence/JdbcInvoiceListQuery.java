package com.vetos.modules.billing.infrastructure.persistence;

import com.vetos.modules.billing.application.InvoiceListQuery;
import com.vetos.modules.billing.application.dto.InvoiceSummary;
import com.vetos.modules.billing.domain.InvoiceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcInvoiceListQuery implements InvoiceListQuery {
    private final JdbcClient jdbc;

    public List<InvoiceSummary> page(UUID tenantId, UUID ownerId, long offset, int limit) {
        String sql = """
            SELECT i.id, i.owner_id, o.full_name, i.total_amount, i.status, i.issued_at
            FROM invoices i JOIN owners o ON o.id = i.owner_id AND o.tenant_id = i.tenant_id
            WHERE i.tenant_id = :tenant
            """ + (ownerId == null ? "" : " AND i.owner_id = :owner ")
            + " ORDER BY i.issued_at DESC NULLS LAST, i.id LIMIT :limit OFFSET :offset";
        var query = jdbc.sql(sql).param("tenant", tenantId).param("limit", limit).param("offset", offset);
        if (ownerId != null) query = query.param("owner", ownerId);
        return query.query((rs, row) -> {
            var issued = rs.getTimestamp("issued_at");
            return new InvoiceSummary(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class),
                rs.getString("full_name"), rs.getBigDecimal("total_amount"), InvoiceStatus.valueOf(rs.getString("status")),
                issued == null ? null : issued.toInstant());
        }).list();
    }
}
