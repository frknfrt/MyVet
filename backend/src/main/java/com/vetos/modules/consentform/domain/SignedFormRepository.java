package com.vetos.modules.consentform.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SignedFormRepository {
    SignedForm save(SignedForm form);
    Optional<SignedForm> findById(UUID id);
    List<SignedForm> findByPatientIdOrderBySignedAtDesc(UUID patientId);
    List<SignedForm> findByOwnerIdOrderBySignedAtDesc(UUID ownerId);
    long countByTemplateId(UUID templateId);
}
