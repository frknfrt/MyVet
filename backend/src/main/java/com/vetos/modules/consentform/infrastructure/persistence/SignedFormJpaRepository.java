package com.vetos.modules.consentform.infrastructure.persistence;

import com.vetos.modules.consentform.domain.SignedForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SignedFormJpaRepository extends JpaRepository<SignedForm, UUID> {
    List<SignedForm> findByPatientIdOrderBySignedAtDesc(UUID patientId);
    List<SignedForm> findByOwnerIdOrderBySignedAtDesc(UUID ownerId);
    long countByTemplateId(UUID templateId);
}
