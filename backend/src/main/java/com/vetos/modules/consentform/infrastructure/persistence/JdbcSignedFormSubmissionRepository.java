package com.vetos.modules.consentform.infrastructure.persistence;

import com.vetos.modules.consentform.domain.SignedFormSubmissionRepository;
import com.vetos.modules.consentform.domain.exception.SignedFormRequestConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
class JdbcSignedFormSubmissionRepository implements SignedFormSubmissionRepository {
    private final JdbcTemplate jdbc;

    @Override
    public Optional<UUID> claim(UUID tenantId, UUID requestId, String requestHash) {
        jdbc.update("""
            INSERT INTO signed_form_submissions (tenant_id, request_id, request_hash)
            VALUES (?, ?, ?) ON CONFLICT (tenant_id, request_id) DO NOTHING
            """, tenantId, requestId, requestHash);
        return jdbc.queryForObject("""
            SELECT request_hash, signed_form_id FROM signed_form_submissions
            WHERE tenant_id = ? AND request_id = ? FOR UPDATE
            """, (row, index) -> {
                if (!requestHash.equals(row.getString("request_hash"))) throw new SignedFormRequestConflictException();
                return Optional.ofNullable(row.getObject("signed_form_id", UUID.class));
            }, tenantId, requestId);
    }

    @Override
    public void complete(UUID tenantId, UUID requestId, UUID signedFormId) {
        int updated = jdbc.update("""
            UPDATE signed_form_submissions SET signed_form_id = ?
            WHERE tenant_id = ? AND request_id = ? AND signed_form_id IS NULL
            """, signedFormId, tenantId, requestId);
        if (updated != 1) throw new IllegalStateException("Form gönderimi tamamlanamadı");
    }
}
