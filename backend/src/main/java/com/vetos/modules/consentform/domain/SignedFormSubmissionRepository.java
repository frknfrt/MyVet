package com.vetos.modules.consentform.domain;

import java.util.Optional;
import java.util.UUID;

public interface SignedFormSubmissionRepository {
    /** Aynı kiracı/anahtar için commit veya rollback bekler. Boş sonuç yeni gönderimin sahipliğidir. */
    Optional<UUID> claim(UUID tenantId, UUID requestId, String requestHash);
    void complete(UUID tenantId, UUID requestId, UUID signedFormId);
}
