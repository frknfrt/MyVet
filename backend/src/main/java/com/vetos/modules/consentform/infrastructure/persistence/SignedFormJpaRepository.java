package com.vetos.modules.consentform.infrastructure.persistence;

import com.vetos.modules.consentform.domain.SignedForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SignedFormJpaRepository extends JpaRepository<SignedForm, UUID> {
    List<SignedForm> findByPatientIdOrderBySignedAtDesc(UUID patientId);
    List<SignedForm> findByPatientIdAndOwnerIdOrderBySignedAtDesc(UUID patientId, UUID ownerId);
    List<SignedForm> findByOwnerIdOrderBySignedAtDesc(UUID ownerId);
    interface TemplateCount {
        UUID getTemplateId();
        long getTotal();
    }

    @Query("select f.templateId as templateId, count(f) as total from SignedForm f "
        + "where f.templateId in :templateIds group by f.templateId")
    List<TemplateCount> countByTemplateIds(@Param("templateIds") List<UUID> templateIds);
}
