package com.vetos.modules.consentform.infrastructure.persistence;

import com.vetos.modules.consentform.domain.PrivacyNotice;
import com.vetos.modules.consentform.domain.PrivacyNoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC sorgularında tenant kimliği her zaman açıkça koşuldadır. */
@Repository
@RequiredArgsConstructor
public class JdbcPrivacyNoticeRepository implements PrivacyNoticeRepository {
    private final JdbcTemplate jdbc;
    public Optional<PrivacyNotice> find(UUID tenantId) {
        return jdbc.query("SELECT version, notice_text FROM clinic_privacy_notices WHERE tenant_id = ?",
            (rs,n) -> new PrivacyNotice(rs.getLong(1), rs.getString(2)), tenantId).stream().findFirst();
    }
    public boolean save(UUID tenantId, String text, long expectedVersion, UUID staffId) {
        if (expectedVersion == 0) return jdbc.update("""
            INSERT INTO clinic_privacy_notices (tenant_id,version,notice_text,updated_by,updated_at)
            VALUES (?,2,?,?,CURRENT_TIMESTAMP) ON CONFLICT (tenant_id) DO NOTHING
            """, tenantId, text, staffId) == 1;
        return jdbc.update("""
            UPDATE clinic_privacy_notices SET version=version+1, notice_text=?, updated_by=?, updated_at=CURRENT_TIMESTAMP
            WHERE tenant_id=? AND version=?
            """, text, staffId, tenantId, expectedVersion) == 1;
    }
    public void recordDelivery(UUID tenantId, UUID ownerId, PrivacyNotice notice, UUID staffId, String clientIp) {
        jdbc.update("""
            INSERT INTO privacy_notice_deliveries
            (id,tenant_id,owner_id,notice_version,notice_hash,notice_text,recorded_by,recorded_at,client_ip)
            VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?)
            """, UUID.randomUUID(), tenantId, ownerId, notice.version(), notice.hash(), notice.text(), staffId, clientIp);
    }
    public List<Delivery> deliveries(UUID tenantId, UUID ownerId) {
        return jdbc.query("""
            SELECT id,notice_version,notice_text,notice_hash,recorded_by,recorded_at FROM privacy_notice_deliveries
            WHERE tenant_id=? AND owner_id=? ORDER BY recorded_at DESC, id
            """, (rs,n) -> new Delivery(rs.getObject(1,UUID.class),rs.getLong(2),rs.getString(3),rs.getString(4),
                rs.getObject(5,UUID.class),rs.getTimestamp(6).toInstant()), tenantId,ownerId);
    }
}
