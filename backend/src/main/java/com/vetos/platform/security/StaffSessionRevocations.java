package com.vetos.platform.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public record StaffSessionRevocations(JdbcTemplate jdbc) {
    public boolean revoked(UUID staffId, long issuedMillis) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM staff_session_revocations
                WHERE staff_id = ? AND revoked_at >= to_timestamp(? / 1000.0))
            """, Boolean.class, staffId, issuedMillis));
    }
}
